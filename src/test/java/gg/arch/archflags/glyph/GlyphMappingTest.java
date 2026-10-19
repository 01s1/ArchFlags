package gg.arch.archflags.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

class GlyphMappingTest {

    /** A logger that records warnings instead of printing them. */
    private static Logger recordingLogger(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = GlyphMappingTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Character> bundledMapping() throws Exception {
        List<String> warnings = new ArrayList<>();
        Map<String, Character> mapping = GlyphMapping.parse(resource("glyph-mapping.yml"), "glyph-mapping.yml",
                recordingLogger(warnings));
        assertEquals(List.of(), warnings);
        return mapping;
    }

    @Test
    void bundledMappingStaysInsideTheArchFlagsRange() throws Exception {
        Map<String, Character> mapping = bundledMapping();
        assertEquals(250, mapping.size());
        assertEquals(mapping.size(), new HashSet<>(mapping.values()).size(), "two countries share a codepoint");
        for (Map.Entry<String, Character> entry : mapping.entrySet()) {
            char glyph = entry.getValue();
            // E9xx belongs to NxRanks; anything outside E200-E2F9 would collide or miss the sheet.
            assertTrue(glyph >= '' && glyph <= '',
                    entry.getKey() + " maps outside E200-E2F9: " + Integer.toHexString(glyph));
        }
    }

    @Test
    void bundledMappingMatchesTheEmbeddedJavaFont() throws Exception {
        Map<String, Character> fromFont = new HashMap<>();
        try (InputStream in = GlyphMappingTest.class.getClassLoader().getResourceAsStream("archflags-java-pack.zip");
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals("assets/archflags/font/flags.json")) {
                    continue;
                }
                JsonObject font = JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : font.getAsJsonArray("providers")) {
                    JsonObject provider = element.getAsJsonObject();
                    String file = provider.get("file").getAsString(); // archflags:font/flags/sa.png
                    String code = file.substring(file.lastIndexOf('/') + 1, file.length() - ".png".length());
                    String chars = provider.getAsJsonArray("chars").get(0).getAsString();
                    assertEquals(1, chars.length(), file);
                    fromFont.put(code.toUpperCase(Locale.ROOT), chars.charAt(0));
                }
                break;
            }
        }
        assertEquals(bundledMapping(), fromFont, "glyph-mapping.yml and the Java pack font disagree");
    }

    @Test
    void parseUpperCasesCodesAndSkipsInvalidCodepoints() throws Exception {
        List<String> warnings = new ArrayList<>();
        Map<String, Character> mapping = GlyphMapping.parse("""
                countries:
                  sa:
                    codepoint: E2B0
                  xx:
                    codepoint: not-hex
                  yy:
                    name: No codepoint
                """, "test.yml", recordingLogger(warnings));

        assertEquals(Map.of("SA", ''), mapping);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("not-hex"));
    }

    @Test
    void missingCountriesSectionIsEmptyNotAnError() throws Exception {
        List<String> warnings = new ArrayList<>();
        assertEquals(Map.of(), GlyphMapping.parse("start-codepoint: E200\n", "test.yml", recordingLogger(warnings)));
        assertEquals(1, warnings.size());
    }

    @Test
    void malformedYamlIsReportedToTheCaller() {
        assertThrows(InvalidConfigurationException.class,
                () -> GlyphMapping.parse("countries: [unclosed", "test.yml", recordingLogger(new ArrayList<>())));
    }

    @Test
    void replaceAllSwapsInAnIndependentCopy() {
        GlyphMapping mapping = new GlyphMapping();
        assertFalse(mapping.isLoaded());
        assertEquals(0, mapping.size());

        Map<String, Character> source = new HashMap<>(Map.of("SA", ''));
        mapping.replaceAll(source);
        source.put("US", '');

        assertTrue(mapping.isLoaded());
        assertEquals(1, mapping.size());
        assertEquals('', mapping.glyphFor("sa"));
        assertNull(mapping.glyphFor("US"), "later edits to the source map must not leak in");
        assertNull(mapping.glyphFor(null));

        mapping.replaceAll(Map.of());
        assertFalse(mapping.isLoaded());
        assertNull(mapping.glyphFor("SA"));
    }
}
