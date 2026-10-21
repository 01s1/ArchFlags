package gg.arch.archflags.data;

import gg.arch.archflags.ArchFlagsPlugin;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;

/**
 * Flag visibility per player. With LuckPerms it is persisted as a permission node through
 * {@link LuckPermsVisibility} (network-wide). Without LuckPerms -- a softdepend -- it falls back to an
 * in-memory, per-server default of "visible", so the rest of the plugin keeps working.
 * <p>
 * This class must never name a LuckPerms type: it is loaded on every server, and the JVM fails to load a
 * class whose code refers to a missing one (1.2.2-rc1 failed to enable without LuckPerms for that reason).
 */
public final class FlagVisibilityStore {

    private final Supplier<String> nodeKey;
    private final ConcurrentHashMap<UUID, Boolean> fallbackCache = new ConcurrentHashMap<>();
    private volatile VisibilityBackend backend;

    public FlagVisibilityStore(ArchFlagsPlugin plugin) {
        this(() -> plugin.archConfig().luckPermsPreferenceNode());
    }

    FlagVisibilityStore(Supplier<String> nodeKey) {
        this.nodeKey = nodeKey;
    }

    public void hookLuckPerms() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            backend = null;
            return;
        }
        try {
            backend = LuckPermsVisibility.connect(nodeKey);
        } catch (LinkageError ex) {
            // A LuckPerms build without the API classes we compiled against: stay on the fallback.
            backend = null;
        }
    }

    public boolean isLuckPermsAvailable() {
        return backend != null;
    }

    /** Reads visibility for an already-loaded (online) player. Defaults to true (visible). */
    public boolean isVisible(UUID uuid) {
        VisibilityBackend current = backend;
        Boolean stored = current == null ? null : current.visibleIfLoaded(uuid);
        return stored != null ? stored : fallbackCache.getOrDefault(uuid, true);
    }

    /** Async lookup that also works for offline players (used by /archflags lookup). */
    public CompletableFuture<Boolean> isVisibleAsync(UUID uuid) {
        VisibilityBackend current = backend;
        if (current == null) {
            return CompletableFuture.completedFuture(fallbackCache.getOrDefault(uuid, true));
        }
        return current.visibleAsync(uuid);
    }

    /** Sets and persists visibility. Completes once the write has been saved to LuckPerms' storage. */
    public CompletableFuture<Void> setVisible(UUID uuid, boolean visible) {
        VisibilityBackend current = backend;
        if (current == null) {
            fallbackCache.put(uuid, visible);
            return CompletableFuture.completedFuture(null);
        }
        return current.setVisible(uuid, visible);
    }
}
