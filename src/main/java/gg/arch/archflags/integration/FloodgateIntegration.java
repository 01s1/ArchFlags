package gg.arch.archflags.integration;

import gg.arch.archflags.ArchFlagsPlugin;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Bedrock-player detection, used only to decide whether to skip sending ArchFlags' JAVA resource
 * pack to a player (Bedrock clients get their pack from Geyser's own pack folder on the proxy
 * instead -- see the main README's "Bedrock detection" section). Country resolution itself does
 * not depend on this class at all -- it works identically for Java and Bedrock players either way.
 * <p>
 * <b>On this network, Geyser + Floodgate run on the Velocity proxy, not on the backend Paper
 * servers.</b> That means {@code org.geysermc.floodgate.api.FloodgateApi} -- Floodgate's official,
 * reliable Bedrock-detection API -- is only reachable from a backend if Floodgate is <i>also</i>
 * installed there, with the proxy's {@code key.pem} copied into its data folder (Floodgate's own
 * documented multi-server setup: <a href="https://geysermc.org/wiki/floodgate/setup/proxy-servers/">
 * Floodgate Proxy Servers Setup</a>). Without that, this class has no reliable way to ask "is this
 * player on Bedrock?" at all, and says so loudly on startup rather than silently guessing.
 * <p>
 * As a stopgap for operators who don't want to install backend Floodgate, an optional (default
 * OFF) username-prefix heuristic is available: Floodgate already rewrites a Bedrock player's
 * visible Java username on the proxy (adding a prefix, "." by default) before forwarding them to
 * any backend, so that prefix is often still visible here even without backend Floodgate. This is
 * NOT an officially documented or guaranteed Floodgate API -- it breaks if the prefix is set to
 * empty, and a very unusual real Java username could theoretically collide with it -- so it's
 * opt-in and always reported as "best-effort" rather than "available".
 * <p>
 * Both detection paths use reflection rather than a compile-time dependency: Floodgate currently
 * only publishes SNAPSHOT builds (no stable released Maven coordinate to pin), the same situation
 * as GriefPrevention in ArchStaffAudit.
 */
public final class FloodgateIntegration {

    private final ArchFlagsPlugin plugin;
    private boolean available;
    private Object apiInstance;
    private Method isFloodgatePlayerMethod;

    private boolean prefixFallbackEnabled;
    private String prefixFallback;

    public FloodgateIntegration(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        available = false;
        apiInstance = null;
        isFloodgatePlayerMethod = null;
        prefixFallbackEnabled = plugin.archConfig().bedrockPrefixFallbackEnabled();
        prefixFallback = plugin.archConfig().bedrockPrefixFallbackPrefix();

        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            plugin.getLogger().warning("Floodgate is NOT installed on this backend server -- reliable Bedrock detection is unavailable.");
            plugin.getLogger().warning("This is expected if Floodgate only runs on your Velocity proxy. For reliable detection, install the");
            plugin.getLogger().warning("Floodgate plugin on this backend too, with the SAME key.pem as the proxy's plugins/floodgate/key.pem");
            plugin.getLogger().warning("(see https://geysermc.org/wiki/floodgate/setup/proxy-servers/ and this plugin's README.md).");
            if (prefixFallbackEnabled) {
                plugin.getLogger().warning("Falling back to the username-prefix heuristic ('" + prefixFallback + "') in the meantime -- best-effort only, not officially reliable.");
            } else {
                plugin.getLogger().warning("bedrock-detection.username-prefix-fallback is disabled, so every player is currently treated as Java "
                        + "(Bedrock players may incorrectly receive the Java resource pack until this is fixed).");
            }
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            apiInstance = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            available = true;
            plugin.getLogger().info("Hooked Floodgate on this backend for reliable Bedrock detection.");
        } catch (Throwable ex) {
            available = false;
            plugin.getLogger().warning("Floodgate is installed but its API shape didn't match what ArchFlags expects (" + ex + "); "
                    + "Bedrock detection will use the fallback heuristic (if enabled) or treat everyone as Java.");
        }
    }

    /** True only when the real, reliable Floodgate API is hooked (backend Floodgate installed). */
    public boolean isAvailable() {
        return available;
    }

    /** True when detection is currently relying on the unreliable username-prefix heuristic instead of the real API. */
    public boolean isUsingFallbackHeuristic() {
        return !available && prefixFallbackEnabled;
    }

    /** Human-readable summary for /archflags status. */
    public String detectionModeDescription() {
        if (available) {
            return "Floodgate API (reliable)";
        }
        if (prefixFallbackEnabled) {
            return "username-prefix heuristic (best-effort, prefix=\"" + prefixFallback + "\")";
        }
        return "unavailable -- all players treated as Java";
    }

    public boolean isBedrockPlayer(String username, UUID uuid) {
        if (available) {
            try {
                return Boolean.TRUE.equals(isFloodgatePlayerMethod.invoke(apiInstance, uuid));
            } catch (Throwable ex) {
                if (plugin.archConfig().debug()) {
                    plugin.getLogger().warning("Floodgate API call failed for " + uuid + ": " + ex);
                }
                // fall through to the heuristic below rather than guessing "Java" outright
            }
        }
        return prefixFallbackEnabled && username != null && !prefixFallback.isEmpty() && username.startsWith(prefixFallback);
    }
}
