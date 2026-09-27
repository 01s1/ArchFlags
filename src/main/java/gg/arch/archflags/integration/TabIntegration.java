package gg.arch.archflags.integration;

import gg.arch.archflags.ArchFlagsPlugin;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Best-effort integration with the TAB plugin's developer API (via reflection -- TAB has no
 * single Maven/JitPack artifact that reliably builds across recent versions on JitPack at the
 * time of writing, the same situation as GriefPrevention in ArchStaffAudit) to force an
 * immediate nametag refresh when a player's flag visibility or resolved country changes,
 * instead of waiting for TAB's own periodic placeholder refresh.
 * <p>
 * Purely an optimization for requirement "/flag must refresh the nametag immediately, without a
 * relog": even if this fails, is unavailable, or TAB's API shape has changed, the placeholder
 * itself is never cached by ArchFlags or PlaceholderAPI, so TAB will still show the new value on
 * its own next regular refresh cycle (typically well under a second).
 */
public final class TabIntegration {

    private static final String[] PLACEHOLDERS = {
            "%archflags_flag%",
            "%archflags_code%",
            "%archflags_country%",
            "%archflags_visible%",
            "%archflags_nametag_suffix%"
    };

    private final ArchFlagsPlugin plugin;
    private boolean available;

    private Object tabApiInstance;
    private Method getPlayerMethod;
    private Method getPlaceholderManagerMethod;
    private Method getPlaceholderMethod;
    private Method updateMethod;
    private Class<?> playerPlaceholderClass;

    public TabIntegration(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            return;
        }
        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Class<?> placeholderManagerClass = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
            playerPlaceholderClass = Class.forName("me.neznamy.tab.api.placeholder.PlayerPlaceholder");
            Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");

            Method getInstance = tabApiClass.getMethod("getInstance");
            tabApiInstance = getInstance.invoke(null);

            getPlayerMethod = tabApiClass.getMethod("getPlayer", UUID.class);
            getPlaceholderManagerMethod = tabApiClass.getMethod("getPlaceholderManager");
            getPlaceholderMethod = placeholderManagerClass.getMethod("getPlaceholder", String.class);
            updateMethod = playerPlaceholderClass.getMethod("update", tabPlayerClass);

            available = true;
            plugin.getLogger().info("Hooked TAB for immediate nametag refresh on flag changes.");
        } catch (Throwable ex) {
            available = false;
            plugin.getLogger().warning("TAB found but its API shape didn't match what ArchFlags expects (" + ex
                    + "); falling back to TAB's own normal placeholder refresh instead -- nametags will still "
                    + "update, just not instantly. ArchFlags itself is unaffected.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Best-effort: forces TAB to immediately re-evaluate our placeholders for this player. */
    public void refresh(UUID uuid) {
        if (!available) {
            return;
        }
        try {
            Object tabPlayer = getPlayerMethod.invoke(tabApiInstance, uuid);
            if (tabPlayer == null) {
                return;
            }
            Object placeholderManager = getPlaceholderManagerMethod.invoke(tabApiInstance);
            for (String identifier : PLACEHOLDERS) {
                Object placeholder = getPlaceholderMethod.invoke(placeholderManager, identifier);
                if (placeholder != null && playerPlaceholderClass.isInstance(placeholder)) {
                    updateMethod.invoke(placeholder, tabPlayer);
                }
            }
        } catch (Throwable ex) {
            // Never let a TAB-side failure break ArchFlags' own command handling -- worst case,
            // the nametag just updates on TAB's own next refresh cycle instead of instantly.
            if (plugin.archConfig().debug()) {
                plugin.getLogger().warning("TAB refresh call failed: " + ex);
            }
        }
    }
}
