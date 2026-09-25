package gg.arch.archflags.util;

import gg.arch.archflags.api.CountryInfo;
import gg.arch.archflags.config.ArchFlagsConfig;
import gg.arch.archflags.config.DisplayMode;
import gg.arch.archflags.glyph.GlyphMapping;

/**
 * Turns a resolved country + visibility choice into the actual placeholder strings. Kept separate
 * from the PlaceholderAPI expansion so the same logic could be reused by ArchFlagsService/future
 * ArchSettings integration without going through PlaceholderAPI at all.
 */
public final class FlagRenderer {

    private FlagRenderer() {
    }

    /** %archflags_flag% */
    public static String flag(CountryInfo info, boolean visible, ArchFlagsConfig cfg, GlyphMapping mapping) {
        if (!visible) {
            return "";
        }
        if (!info.isResolved()) {
            return cfg.unknownFlag();
        }
        String rendered = renderMode(cfg.displayMode(), info, cfg, mapping);
        return rendered != null ? rendered : "";
    }

    private static String renderMode(DisplayMode mode, CountryInfo info, ArchFlagsConfig cfg, GlyphMapping mapping) {
        switch (mode) {
            case CUSTOM_GLYPH: {
                Character glyph = mapping.glyphFor(info.isoCode());
                if (glyph == null) {
                    // No generated asset for this country (or mapping not loaded) -- fall back
                    // instead of ever emitting a broken-square tofu glyph.
                    return renderMode(cfg.fallbackMode(), info, cfg, mapping);
                }
                String glyphStr = String.valueOf(glyph.charValue());
                if (cfg.wrapMiniMessage()) {
                    return "<font:" + cfg.fontKey() + ">" + glyphStr + "</font>";
                }
                return glyphStr;
            }
            case UNICODE: {
                String unicodeFlag = UnicodeFlag.regionalIndicatorFlag(info.isoCode());
                if (unicodeFlag == null) {
                    return renderMode(DisplayMode.COUNTRY_CODE, info, cfg, mapping);
                }
                return unicodeFlag;
            }
            case COUNTRY_CODE:
            default:
                return "[" + info.isoCode() + "]";
        }
    }

    /** %archflags_nametag_suffix% */
    public static String nametagSuffix(CountryInfo info, boolean visible, ArchFlagsConfig cfg, GlyphMapping mapping) {
        String flag = flag(info, visible, cfg, mapping);
        if (flag.isEmpty()) {
            return "";
        }
        return cfg.nametagSeparator() + flag;
    }

    /** %archflags_code% */
    public static String code(CountryInfo info, ArchFlagsConfig cfg) {
        return info.isResolved() ? info.isoCode() : cfg.unknownCode();
    }

    /** %archflags_country% */
    public static String country(CountryInfo info, ArchFlagsConfig cfg) {
        return info.isResolved() ? info.name() : cfg.unknownCountry();
    }
}
