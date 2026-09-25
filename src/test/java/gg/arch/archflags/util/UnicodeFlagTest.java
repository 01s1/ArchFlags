package gg.arch.archflags.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UnicodeFlagTest {

    @Test
    void buildsSaudiArabiaFlag() {
        String expected = new String(Character.toChars(0x1F1F8)) + new String(Character.toChars(0x1F1E6));
        assertEquals(expected, UnicodeFlag.regionalIndicatorFlag("SA"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(UnicodeFlag.regionalIndicatorFlag("US"), UnicodeFlag.regionalIndicatorFlag("us"));
    }

    @Test
    void rejectsWrongLength() {
        assertNull(UnicodeFlag.regionalIndicatorFlag("USA"));
        assertNull(UnicodeFlag.regionalIndicatorFlag("U"));
        assertNull(UnicodeFlag.regionalIndicatorFlag(""));
    }

    @Test
    void rejectsNonLetters() {
        assertNull(UnicodeFlag.regionalIndicatorFlag("U1"));
        assertNull(UnicodeFlag.regionalIndicatorFlag(null));
    }
}
