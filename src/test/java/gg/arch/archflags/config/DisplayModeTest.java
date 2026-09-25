package gg.arch.archflags.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DisplayModeTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertEquals(DisplayMode.CUSTOM_GLYPH, DisplayMode.parse("custom_glyph", DisplayMode.UNICODE));
        assertEquals(DisplayMode.UNICODE, DisplayMode.parse("Unicode", DisplayMode.COUNTRY_CODE));
        assertEquals(DisplayMode.COUNTRY_CODE, DisplayMode.parse(" COUNTRY_CODE ", DisplayMode.UNICODE));
    }

    @Test
    void fallsBackOnNullOrGarbage() {
        assertEquals(DisplayMode.CUSTOM_GLYPH, DisplayMode.parse(null, DisplayMode.CUSTOM_GLYPH));
        assertEquals(DisplayMode.CUSTOM_GLYPH, DisplayMode.parse("not-a-mode", DisplayMode.CUSTOM_GLYPH));
    }
}
