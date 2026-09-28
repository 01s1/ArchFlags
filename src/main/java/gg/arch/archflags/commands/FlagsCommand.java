package gg.arch.archflags.commands;

import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.events.ArchFlagVisibilityChangeEvent;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/**
 * /flag (primary), /flags (alias) -- on/off/toggle/status for the sender's own country flag
 * visibility. The change is persisted via LuckPerms (network-wide) and reflected in the nametag
 * immediately -- no relog required, see {@link gg.arch.archflags.integration.TabIntegration}.
 */
public final class FlagsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "toggle", "status");

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

        if (args.length == 0) {
            sendStatus(player, current);
            return true;
        }
        if (args.length > 1 || !SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            player.sendMessage(plugin.archConfig().prefixedMessage("flag-usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> apply(player, uuid, true, current);
            case "off" -> apply(player, uuid, false, current);
            case "toggle" -> apply(player, uuid, !current, current);
            case "status" -> sendStatus(player, current);
            default -> throw new IllegalStateException("unreachable: filtered by SUBCOMMANDS check above");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("archflags.command") || args.length != 1) {
            return List.of();
        }
        return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new java.util.ArrayList<>());
    }

    private void sendStatus(Player player, boolean current) {
        player.sendMessage(plugin.archConfig().prefixedMessage(current ? "flag-status-on" : "flag-status-off"));
    }

    private void apply(Player player, UUID uuid, boolean newValue, boolean previous) {
        plugin.visibilityStore().setVisible(uuid, newValue).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getPluginManager().callEvent(new ArchFlagVisibilityChangeEvent(uuid, newValue, previous));
                    plugin.tabIntegration().refresh(uuid);
                    player.sendMessage(plugin.archConfig().prefixedMessage(newValue ? "flag-enabled" : "flag-disabled"));
                }));
    }
}
