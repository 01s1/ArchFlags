package gg.arch.archflags.util;

/** Pure, dependency-free helper for the standard Unicode "regional indicator symbol" flag emoji. */
public final class UnicodeFlag {

    private static final int REGIONAL_INDICATOR_BASE = 0x1F1E6;

    private UnicodeFlag() {
    }

    /**
     * Builds the flag emoji for a 2-letter code, e.g. "SA" -> the Saudi Arabia flag emoji.
     * Returns null for anything that isn't exactly two ASCII letters.
     */
    public static String regionalIndicatorFlag(String isoCode) {
        if (isoCode == null || isoCode.length() != 2) {
            return null;
        }
        char a = Character.toUpperCase(isoCode.charAt(0));
        char b = Character.toUpperCase(isoCode.charAt(1));
        if (a < 'A' || a > 'Z' || b < 'A' || b > 'Z') {
            return null;
        }
        int first = REGIONAL_INDICATOR_BASE + (a - 'A');
        int second = REGIONAL_INDICATOR_BASE + (b - 'A');
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }
}
