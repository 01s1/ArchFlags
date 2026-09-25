package gg.arch.archflags.config;

public enum DisplayMode {
    CUSTOM_GLYPH,
    UNICODE,
    COUNTRY_CODE;

    public static DisplayMode parse(String raw, DisplayMode fallback) {
        if (raw == null) return fallback;
        try {
            return DisplayMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
