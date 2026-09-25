package gg.arch.archflags.placeholder;

import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.api.CountryInfo;
import gg.arch.archflags.util.FlagRenderer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Provides:
 *   %archflags_flag%              -- rendered glyph/emoji/code, or "" if hidden/unresolved-hidden
 *   %archflags_code%               -- ISO alpha-2 code, or the configured unknown code
 *   %archflags_country%            -- English country name, or the configured unknown name
 *   %archflags_visible%            -- "true"/"false"
 *   %archflags_nametag_suffix%     -- separator + flag, or "" if the flag itself is empty
 * <p>
 * persist() = true so it survives /papi reload without ArchFlags needing to re-register it.
 * Intentionally does NOT provide a placeholder meant for chat/TAB-list use -- see README.md.
 */
public final class ArchFlagsExpansion extends PlaceholderExpansion {

    private final ArchFlagsPlugin plugin;

    public ArchFlagsExpansion(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "archflags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ARCH";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        var uuid = player.getUniqueId();
        CountryInfo info = plugin.geoIpService().getCountry(uuid);
        boolean visible = plugin.visibilityStore().isVisible(uuid);
        var cfg = plugin.archConfig();
        var mapping = plugin.glyphMapping();

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "flag" -> FlagRenderer.flag(info, visible, cfg, mapping);
            case "code" -> FlagRenderer.code(info, cfg);
            case "country" -> FlagRenderer.country(info, cfg);
            case "visible" -> String.valueOf(visible);
            case "nametag_suffix" -> FlagRenderer.nametagSuffix(info, visible, cfg, mapping);
            default -> null;
        };
    }
}
