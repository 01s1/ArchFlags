package gg.arch.archflags.commands;

import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.events.ArchFlagVisibilityChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /flag (primary), /flags (alias) -- on/off/toggle/status for the sender's own country flag
 * visibility. The change is persisted via LuckPerms (network-wide) and reflected in the nametag
 * immediately -- no relog required, see {@link gg.arch.archflags.integration.TabIntegration}.
 */
public final class FlagsCommand implements CommandExecutor {

    private final ArchFlagsPlugin plugin;

    public FlagsCommand(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.archConfig().prefixedMessage("player-only"));
            return true;
        }
        if (!player.hasPermission("archflags.command")) {
            player.sendMessage(plugin.archConfig().prefixedMessage("no-permission"));
            return true;
        }

        var uuid = player.getUniqueId();
        boolean current = plugin.visibilityStore().isVisible(uuid);

        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "status";
        switch (sub) {
            case "on" -> apply(player, uuid, true, current);
            case "off" -> apply(player, uuid, false, current);
            case "toggle" -> apply(player, uuid, !current, current);
            case "status" -> sendStatus(player, current);
            default -> sendStatus(player, current);
        }
        return true;
    }

    private void sendStatus(Player player, boolean current) {
        player.sendMessage(plugin.archConfig().prefixedMessage("flag-status")
                .replace("%state%", current ? "ON" : "OFF"));
    }

    private void apply(Player player, java.util.UUID uuid, boolean newValue, boolean previous) {
        plugin.visibilityStore().setVisible(uuid, newValue).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getPluginManager().callEvent(new ArchFlagVisibilityChangeEvent(uuid, newValue, previous));
                    plugin.tabIntegration().refresh(uuid);
                    player.sendMessage(plugin.archConfig().prefixedMessage(newValue ? "flag-enabled" : "flag-disabled"));
                }));
    }
}
