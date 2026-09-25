package gg.arch.archflags.listeners;

import gg.arch.archflags.ArchFlagsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Kicks off country resolution as early as possible (pre-login, already off the main thread) so
 * it's usually ready by the time the player's nametag first renders, and schedules cache eviction
 * on quit. Never touches TAB, chat, the player list, or scoreboards.
 */
public final class PlayerConnectionListener implements Listener {

    private final ArchFlagsPlugin plugin;

    public PlayerConnectionListener(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        // Fire-and-forget: resolution completes on its own executor and populates the cache;
        // we don't block login waiting for it.
        plugin.geoIpService().resolve(event.getUniqueId(), event.getAddress());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.geoIpService().scheduleEviction(event.getPlayer().getUniqueId());
    }
}
