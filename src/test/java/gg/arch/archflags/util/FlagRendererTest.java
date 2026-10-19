package gg.arch.archflags.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.arch.archflags.api.CountryInfo;
import gg.arch.archflags.config.ArchFlagsConfig;
import gg.arch.archflags.glyph.GlyphMapping;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FlagRendererTest {

    private static final CountryInfo SAUDI = new CountryInfo("SA", "Saudi Arabia", true);
    private static final char SA_GLYPH = '';

    /** The bundled config.yml, overridden by {@code yaml} -- the same layering the plugin uses. */
    private static ArchFlagsConfig config(String yaml) throws IOException {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        try (InputStream in = FlagRendererTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        return new ArchFlagsConfig(Logger.getLogger("test"), file);
    }

    private static GlyphMapping mappingWithSaudi() {
        GlyphMapping mapping = new GlyphMapping();
        mapping.replaceAll(Map.of("SA", SA_GLYPH));
        return mapping;
    }

    private static String unicodeSaudi() {
        return UnicodeFlag.regionalIndicatorFlag("SA");
    }

    @Test
    void hiddenFlagRendersNothingAndNoSeparator() throws IOException {
        ArchFlagsConfig cfg = config("");
        assertEquals("", FlagRenderer.flag(SAUDI, false, cfg, mappingWithSaudi()));
        assertEquals("", FlagRenderer.nametagSuffix(SAUDI, false, cfg, mappingWithSaudi()));
    }

    @Test
    void customGlyphIsWrappedInTheConfiguredFont() throws IOException {
        ArchFlagsConfig cfg = config("");
        String expected = "<font:archflags:flags>" + SA_GLYPH + "</font>";
        assertEquals(expected, FlagRenderer.flag(SAUDI, true, cfg, mappingWithSaudi()));
        assertEquals(" | " + expected, FlagRenderer.nametagSuffix(SAUDI, true, cfg, mappingWithSaudi()));
    }

    @Test
    void customGlyphWithoutMiniMessageWrapIsTheBareGlyph() throws IOException {
        ArchFlagsConfig cfg = config("glyphs:\n  wrap-minimessage: false\n");
        assertEquals(String.valueOf(SA_GLYPH), FlagRenderer.flag(SAUDI, true, cfg, mappingWithSaudi()));
    }

    @Test
    void missingGlyphUsesTheFallbackModeInsteadOfTofu() throws IOException {
        GlyphMapping empty = new GlyphMapping();
        assertEquals("[SA]", FlagRenderer.flag(SAUDI, true, config(""), empty));
        assertEquals(unicodeSaudi(), FlagRenderer.flag(SAUDI, true, config("display:\n  fallback-mode: UNICODE\n"), empty));
    }

    @Test
    void unicodeModeUsesRegionalIndicatorsAndFallsBackToTheCode() throws IOException {
        ArchFlagsConfig cfg = config("display:\n  mode: UNICODE\n");
        assertEquals(unicodeSaudi(), FlagRenderer.flag(SAUDI, true, cfg, new GlyphMapping()));
        CountryInfo odd = new CountryInfo("A1", "Anonymous Proxy", true);
        assertEquals("[A1]", FlagRenderer.flag(odd, true, cfg, new GlyphMapping()));
    }

    @Test
    void countryCodeModeIgnoresTheGlyphMapping() throws IOException {
        ArchFlagsConfig cfg = config("display:\n  mode: COUNTRY_CODE\n");
        assertEquals("[SA]", FlagRenderer.flag(SAUDI, true, cfg, mappingWithSaudi()));
    }

    @Test
    void unresolvedCountryShowsTheConfiguredUnknownFlag() throws IOException {
        ArchFlagsConfig cfg = config("");
        assertEquals("[UN]", FlagRenderer.flag(CountryInfo.UNKNOWN, true, cfg, mappingWithSaudi()));
        assertEquals(" | [UN]", FlagRenderer.nametagSuffix(CountryInfo.UNKNOWN, true, cfg, mappingWithSaudi()));
    }

    @Test
    void blankUnknownFlagHidesTheWholeSuffix() throws IOException {
        ArchFlagsConfig cfg = config("display:\n  unknown-flag: \"\"\n");
        assertEquals("", FlagRenderer.nametagSuffix(CountryInfo.UNKNOWN, true, cfg, mappingWithSaudi()));
    }

    @Test
    void codeAndCountryFallBackToConfiguredUnknownValues() throws IOException {
        ArchFlagsConfig cfg = config("display:\n  unknown-code: \"??\"\n  unknown-country: \"Nowhere\"\n");
        assertEquals("SA", FlagRenderer.code(SAUDI, cfg));
        assertEquals("Saudi Arabia", FlagRenderer.country(SAUDI, cfg));
        assertEquals("??", FlagRenderer.code(CountryInfo.UNKNOWN, cfg));
        assertEquals("Nowhere", FlagRenderer.country(CountryInfo.UNKNOWN, cfg));
    }
}
