package gg.arch.archflags.integration;

import gg.arch.archflags.ArchFlagsPlugin;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Best-effort Bedrock-player detection via Floodgate's API, called only to decide whether to
 * skip sending ArchFlags' JAVA resource pack to a player (Bedrock clients get their pack from
 * Geyser's own pack folder instead -- see resourcepack.README). Uses reflection rather than a
 * compile-time dependency: Floodgate currently only publishes SNAPSHOT builds (no stable
 * released Maven coordinate to pin), the same situation as GriefPrevention in ArchStaffAudit.
 * Country resolution itself does not depend on this class at all -- it works identically for
 * Java and Bedrock players either way.
 */
public final class FloodgateIntegration {

    private final ArchFlagsPlugin plugin;
    private boolean available;
    private Object apiInstance;
    private Method isFloodgatePlayerMethod;

    public FloodgateIntegration(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            apiInstance = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            available = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            available = false;
            if (plugin.archConfig().debug()) {
                plugin.getLogger().warning("Floodgate found but its API shape didn't match what ArchFlags expects (" + ex + ").");
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!available) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isFloodgatePlayerMethod.invoke(apiInstance, uuid));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
