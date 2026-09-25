package gg.arch.archflags.geo;

import com.maxmind.db.Reader;
import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.api.CountryInfo;
import gg.arch.archflags.events.ArchCountryResolvedEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wraps a local MaxMind GeoLite2-Country.mmdb lookup. No network calls, ever. Never retains a raw
 * IP address beyond the single lookup call that resolves it, never logs one.
 * <p>
 * The database is opened off the main thread on enable, and every lookup runs on a dedicated
 * single-thread executor -- {@link #getCountry(UUID)} (the only method safe to call from the main
 * thread synchronously) only ever reads an in-memory cache.
 */
public final class GeoIpService {

    private final ArchFlagsPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ArchFlags-GeoIP");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<UUID, CountryInfo> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> pendingEviction = new ConcurrentHashMap<>();

    private volatile Reader reader;
    private volatile boolean loadAttempted = false;

    public GeoIpService(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the .mmdb file off the main thread. Safe to call again after a config reload. */
    public void loadDatabaseAsync() {
        executor.submit(() -> {
            loadAttempted = true;
            closeReaderQuietly();

            File dbFile = new File(plugin.getDataFolder(), plugin.archConfig().geoipDatabaseFile());
            if (!dbFile.exists()) {
                plugin.getLogger().warning("GeoIP database not found at " + dbFile.getName()
                        + " -- place a GeoLite2-Country.mmdb there. Country resolution will report Unknown until then.");
                reader = null;
                return;
            }
            try {
                reader = new Reader(dbFile);
                plugin.getLogger().info("Loaded GeoIP database (" + dbFile.getName() + ").");
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to open GeoIP database: " + ex.getMessage());
                reader = null;
            }
        });
    }

    public boolean isAvailable() {
        return reader != null;
    }

    public boolean hasAttemptedLoad() {
        return loadAttempted;
    }

    /** Non-blocking read of whatever is currently cached; CountryInfo.UNKNOWN if nothing yet. */
    public CountryInfo getCountry(UUID uuid) {
        return cache.getOrDefault(uuid, CountryInfo.UNKNOWN);
    }

    /**
     * Resolves (or returns the cached result for) a player's country. Never blocks the calling
     * thread -- the MaxMind lookup itself always runs on the dedicated executor.
     */
    public CompletableFuture<CountryInfo> resolve(UUID uuid, InetAddress address) {
        cancelPendingEviction(uuid);

        CountryInfo cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<CountryInfo> future = new CompletableFuture<>();
        executor.submit(() -> {
            CountryInfo result = lookup(address);
            cache.put(uuid, result);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(new ArchCountryResolvedEvent(uuid, result));
                future.complete(result);
            });
        });
        return future;
    }

    private CountryInfo lookup(InetAddress address) {
        if (address == null) {
            return CountryInfo.UNKNOWN;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
            // Private/local addresses (LAN test servers, loopback proxies, etc.) are never sent
            // to MaxMind lookups -- there's nothing meaningful to resolve, and we should not risk
            // treating them as a real public geolocation.
            return CountryInfo.UNKNOWN;
        }

        Reader r = this.reader;
        if (r == null) {
            return CountryInfo.UNKNOWN;
        }

        try {
            Map<String, Object> result = r.get(address, Map.class);
            if (result == null) {
                return CountryInfo.UNKNOWN;
            }
            Object countryObj = result.get("country");
            if (!(countryObj instanceof Map)) {
                return CountryInfo.UNKNOWN;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> country = (Map<String, Object>) countryObj;

            Object isoCodeObj = country.get("iso_code");
            if (isoCodeObj == null) {
                return CountryInfo.UNKNOWN;
            }
            String isoCode = String.valueOf(isoCodeObj).toUpperCase(Locale.ROOT);

            String name = isoCode;
            Object namesObj = country.get("names");
            if (namesObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> names = (Map<String, Object>) namesObj;
                Object en = names.get("en");
                if (en != null) {
                    name = String.valueOf(en);
                }
            }
            return new CountryInfo(isoCode, name, true);
        } catch (IOException | RuntimeException ex) {
            // Never log the address itself -- only that a lookup failed.
            if (plugin.archConfig().debug()) {
                plugin.getLogger().warning("GeoIP lookup failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return CountryInfo.UNKNOWN;
        }
    }

    /** Called on quit: keeps the cache warm for a short window in case of a quick reconnect. */
    public void scheduleEviction(UUID uuid) {
        if (!plugin.archConfig().cacheEnabled()) {
            cache.remove(uuid);
            return;
        }
        cancelPendingEviction(uuid);
        long delayTicks = plugin.archConfig().retainAfterQuitMinutes() * 60L * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cache.remove(uuid);
            pendingEviction.remove(uuid);
        }, delayTicks);
        pendingEviction.put(uuid, task);
    }

    private void cancelPendingEviction(UUID uuid) {
        BukkitTask task = pendingEviction.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (BukkitTask task : pendingEviction.values()) {
            task.cancel();
        }
        pendingEviction.clear();
        cache.clear();
        closeReaderQuietly();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void closeReaderQuietly() {
        Reader r = this.reader;
        this.reader = null;
        if (r != null) {
            try {
                r.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
    }
}
