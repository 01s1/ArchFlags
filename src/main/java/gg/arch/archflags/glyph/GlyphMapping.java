package gg.arch.archflags.glyph;

import gg.arch.archflags.ArchFlagsPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the generated country -> glyph codepoint mapping (glyph-mapping.yml, produced by
 * tools/asset-generator/generate.mjs -- see that tool's README for regenerating it). Nothing in
 * this class hard-codes a country's codepoint; it's entirely data-driven.
 */
public final class GlyphMapping {

    private final Map<String, Character> codeToGlyph = new HashMap<>();
    private boolean loaded = false;

    public void load(ArchFlagsPlugin plugin, String fileName) {
        codeToGlyph.clear();
        loaded = false;

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            // Ship a default copy alongside config.yml/plugin.yml so CUSTOM_GLYPH mode works
            // out of the box; the generator can still overwrite it later.
            plugin.saveResource("glyph-mapping.yml", false);
        }
        if (!file.exists()) {
            plugin.getLogger().warning("Glyph mapping file " + fileName + " not found; CUSTOM_GLYPH mode will fall back.");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);

            if (!yaml.isConfigurationSection("countries")) {
                plugin.getLogger().warning("Glyph mapping file " + fileName + " has no 'countries' section.");
                return;
            }

            for (String code : yaml.getConfigurationSection("countries").getKeys(false)) {
                String hex = yaml.getString("countries." + code + ".codepoint");
                if (hex == null) continue;
                try {
                    int codepoint = Integer.parseInt(hex.trim(), 16);
                    codeToGlyph.put(code.toUpperCase(Locale.ROOT), (char) codepoint);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Invalid codepoint '" + hex + "' for country " + code + " in " + fileName);
                }
            }
            loaded = !codeToGlyph.isEmpty();
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            plugin.getLogger().warning("Failed to load glyph mapping file " + fileName + ": " + ex.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
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
