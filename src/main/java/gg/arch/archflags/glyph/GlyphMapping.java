package gg.arch.archflags.glyph;

import gg.arch.archflags.ArchFlagsPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the generated country -> glyph codepoint mapping (glyph-mapping.yml, produced by
 * tools/asset-generator/generate.mjs -- see that tool's README for regenerating it). Nothing in
 * this class hard-codes a country's codepoint; it's entirely data-driven.
 * <p>
 * TAB resolves placeholders on its own thread, so a reload builds the new map off to the side and
 * publishes it with a single volatile write; readers never see a half-filled map.
 */
public final class GlyphMapping {

    private volatile Map<String, Character> codeToGlyph = Map.of();

    public void load(ArchFlagsPlugin plugin, String fileName) {

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            // Ship a default copy alongside config.yml/plugin.yml so CUSTOM_GLYPH mode works
            // out of the box; the generator can still overwrite it later.
            plugin.saveResource("glyph-mapping.yml", false);
        }
        if (!file.exists()) {
            plugin.getLogger().warning("Glyph mapping file " + fileName + " not found; CUSTOM_GLYPH mode will fall back.");
            replaceAll(Map.of());
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            replaceAll(parse(content, fileName, plugin.getLogger()));
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("Failed to load glyph mapping file " + fileName + ": " + ex.getMessage());
            replaceAll(Map.of());
        }
    }

    /** Parses glyph-mapping.yml content into ISO code (upper case) -> glyph. */
    public static Map<String, Character> parse(String yamlContent, String fileName, Logger logger)
            throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(yamlContent);
        if (!yaml.isConfigurationSection("countries")) {
            logger.warning("Glyph mapping file " + fileName + " has no 'countries' section.");
            return Map.of();
        }
        Map<String, Character> result = new HashMap<>();
        for (String code : yaml.getConfigurationSection("countries").getKeys(false)) {
            String hex = yaml.getString("countries." + code + ".codepoint");
            if (hex == null) continue;
            try {
                int codepoint = Integer.parseInt(hex.trim(), 16);
                result.put(code.toUpperCase(Locale.ROOT), (char) codepoint);
            } catch (NumberFormatException ex) {
                logger.warning("Invalid codepoint '" + hex + "' for country " + code + " in " + fileName);
            }
        }
        return result;
    }

    /** Atomically replaces the whole mapping. */
    public void replaceAll(Map<String, Character> mapping) {
        this.codeToGlyph = Map.copyOf(mapping);
    }

    public boolean isLoaded() {
        return !codeToGlyph.isEmpty();
    }

    /** Returns the glyph character for an ISO alpha-2 code, or null if not mapped. */
    public Character glyphFor(String isoCode) {
        if (isoCode == null) return null;
        return codeToGlyph.get(isoCode.toUpperCase(Locale.ROOT));
    }

    public int size() {
        return codeToGlyph.size();
    }
}
