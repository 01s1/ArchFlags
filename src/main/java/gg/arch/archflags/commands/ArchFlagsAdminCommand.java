package gg.arch.archflags.commands;

import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.api.CountryInfo;
import gg.arch.archflags.util.FlagRenderer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /archflags reload | lookup <player> | status -- never reveals a raw IP address. */
public final class ArchFlagsAdminCommand implements CommandExecutor {

    private final ArchFlagsPlugin plugin;

    public ArchFlagsAdminCommand(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /archflags <reload|lookup <player>|status>");
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload" -> {
                if (!requirePermission(sender, "archflags.admin.reload")) return true;
                plugin.reload();
                sender.sendMessage(plugin.archConfig().prefixedMessage("reload-success"));
            }
            case "lookup" -> {
                if (!requirePermission(sender, "archflags.admin.lookup")) return true;
                lookup(sender, args);
            }
            case "status" -> {
                if (!requirePermission(sender, "archflags.admin.status")) return true;
                status(sender);
            }
            default -> sender.sendMessage("Usage: /archflags <reload|lookup <player>|status>");
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(plugin.archConfig().prefixedMessage("no-permission"));
            return false;
        }
        return true;
    }

    private void lookup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /archflags lookup <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(plugin.archConfig().prefixedMessage("player-not-found"));
            return;
        }

        var uuid = target.getUniqueId();
        CountryInfo info = plugin.geoIpService().getCountry(uuid);
        plugin.visibilityStore().isVisibleAsync(uuid).thenAccept(visible ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var cfg = plugin.archConfig();
                    sender.sendMessage(cfg.prefixedMessage("admin-lookup-header"));
                    sender.sendMessage(cfg.message("admin-lookup-player").replace("%player%", String.valueOf(target.getName())));
                    sender.sendMessage(cfg.message("admin-lookup-country").replace("%country%", FlagRenderer.country(info, cfg)));
                    sender.sendMessage(cfg.message("admin-lookup-code").replace("%code%", FlagRenderer.code(info, cfg)));
                    sender.sendMessage(cfg.message("admin-lookup-visible").replace("%visible%", String.valueOf(visible)));
                    if (!info.isResolved()) {
                        sender.sendMessage("&7(No cached resolution -- only online or recently-quit players have one; ArchFlags never stores raw IPs.)"
                                .replace("&7", org.bukkit.ChatColor.GRAY.toString()));
                    }
                }));
    }

    private void status(CommandSender sender) {
        var cfg = plugin.archConfig();
        sender.sendMessage(cfg.prefixedMessage("status-header"));
        sender.sendMessage("GeoIP database loaded: " + plugin.geoIpService().isAvailable());
        sender.sendMessage("Glyph mapping loaded: " + plugin.glyphMapping().isLoaded() + " (" + plugin.glyphMapping().size() + " countries)");
        sender.sendMessage("Display mode: " + cfg.displayMode() + " (fallback " + cfg.fallbackMode() + ")");
        sender.sendMessage("LuckPerms hooked: " + plugin.visibilityStore().isLuckPermsAvailable());
        sender.sendMessage("PlaceholderAPI hooked: " + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null));
        sender.sendMessage("TAB hooked (immediate refresh): " + plugin.tabIntegration().isAvailable());
        sender.sendMessage("Floodgate hooked (Bedrock detection): " + plugin.floodgateIntegration().isAvailable());
        sender.sendMessage("Java resource pack: " + (cfg.javaPackEnabled() ? "enabled" : "disabled"));
        sender.sendMessage("Debug: " + cfg.debug());
    }
}
