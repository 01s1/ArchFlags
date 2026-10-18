package gg.arch.archflags.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMigratorTest {

    private static final Logger LOG = Logger.getLogger("ConfigMigratorTest");

    static YamlConfiguration bundledDefaults() throws IOException {
        try (InputStream in = ConfigMigratorTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static File copyFixture(Path dir) throws IOException {
        File target = dir.resolve("config.yml").toFile();
        try (InputStream in = ConfigMigratorTest.class.getClassLoader().getResourceAsStream("fixtures/config-1.0.0.yml")) {
            Files.copy(in, target.toPath());
        }
        return target;
    }

    @Test
    void bundledConfigDeclaresCurrentVersion() throws IOException {
        assertEquals(ConfigMigrator.CURRENT_VERSION, bundledDefaults().getInt(ConfigMigrator.VERSION_KEY));
    }

    @Test
    void upgradesA100ConfigAdditivelyWithBackup(@TempDir Path dir) throws Exception {
        File config = copyFixture(dir);
        String original = Files.readString(config.toPath());

        ConfigMigrator.Result result = ConfigMigrator.migrate(config, bundledDefaults(), LOG);

        assertTrue(result.migrated());
        assertEquals(1, result.fromVersion());
        assertEquals(original, Files.readString(result.backup().toPath()), "backup must be byte-identical");

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(config);
        assertEquals(ConfigMigrator.CURRENT_VERSION, migrated.getInt(ConfigMigrator.VERSION_KEY));
        // New keys arrive with bundled defaults...
        assertEquals("&fCountry flag: &aON", migrated.getString("messages.flag-status-on"));
        assertEquals("&cUsage: /flag <on|off|toggle|status>", migrated.getString("messages.flag-usage"));
        assertTrue(migrated.isConfigurationSection("resourcepack.java.self-host"));
        assertFalse(migrated.getBoolean("bedrock-detection.username-prefix-fallback.enabled", true));
        assertTrue(result.addedKeys().contains("messages.flag-save-failed"));
        // ...while the owner's existing values are untouched, including keys no longer bundled.
        assertEquals("&8[&bArchFlags&8] &r", migrated.getString("messages.prefix"));
        assertEquals("&aFlag visibility enabled.", migrated.getString("messages.flag-enabled"));
        assertEquals("&7Your flag is currently &f%state%&7.", migrated.getString("messages.flag-status"));
        // Bundled comments travel with the added sections.
        assertFalse(migrated.getComments("resourcepack").isEmpty());
    }

    @Test
    void secondRunIsANoOp(@TempDir Path dir) throws Exception {
        File config = copyFixture(dir);
        ConfigMigrator.migrate(config, bundledDefaults(), LOG);
        String afterFirst = Files.readString(config.toPath());

        ConfigMigrator.Result second = ConfigMigrator.migrate(config, bundledDefaults(), LOG);

        assertFalse(second.migrated());
        assertNull(second.backup());
        assertEquals(afterFirst, Files.readString(config.toPath()));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".bak")).count());
        }
    }

    @Test
    void missingFileIsLeftForSaveDefaultConfig(@TempDir Path dir) throws Exception {
        File config = dir.resolve("config.yml").toFile();
        assertFalse(ConfigMigrator.migrate(config, bundledDefaults(), LOG).migrated());
        assertFalse(config.exists());
    }

    @Test
    void neverReplacesAScalarWithADefaultSection() throws Exception {
        YamlConfiguration target = new YamlConfiguration();
        target.set("resourcepack", "custom");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("resourcepack.java.enabled", true);
        defaults.set("other", 1);

        List<String> added = ConfigMigrator.addMissingKeys(target, defaults);

        assertEquals(List.of("other"), added);
        assertEquals("custom", target.getString("resourcepack"));
    }
}
