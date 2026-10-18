package gg.arch.archflags.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ArchFlagsConfigTest {

    @SuppressWarnings("deprecation")
    private static String colored(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    @Test
    void messagesMissingFromAnOldFileFallBackToBundledDefaults() throws Exception {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(new StringReader(
                "messages:\n  prefix: \"&8[Old] \"\n  flag-enabled: \"&aOld enabled\"\n"));
        file.setDefaults(ConfigMigratorTest.bundledDefaults());

        ArchFlagsConfig cfg = new ArchFlagsConfig(Logger.getLogger("test"), file);

        assertEquals(colored("&aOld enabled"), cfg.message("flag-enabled"), "file value wins");
        assertEquals(colored("&fCountry flag: &aON"), cfg.message("flag-status-on"), "default fills the gap");
        assertEquals(colored("&8[Old] &cUsage: /flag <on|off|toggle|status>"), cfg.prefixedMessage("flag-usage"));
    }

    @Test
    void unknownKeyStillEchoesTheKey() throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.setDefaults(ConfigMigratorTest.bundledDefaults());
        assertEquals("no-such-message", new ArchFlagsConfig(Logger.getLogger("test"), file).message("no-such-message"));
    }

    /**
     * Bukkit's getX(path, def) returns the code-level def for a missing key and ignores the
     * bundled defaults, so the two must agree or a trimmed config behaves differently.
     */
    @Test
    void codeDefaultsMatchTheBundledConfig() throws Exception {
        ArchFlagsConfig code = new ArchFlagsConfig(Logger.getLogger("test"), new YamlConfiguration());
        YamlConfiguration bundled = ConfigMigratorTest.bundledDefaults();
        ArchFlagsConfig file = new ArchFlagsConfig(Logger.getLogger("test"), bundled);

        assertEquals(file.geoipDatabaseFile(), code.geoipDatabaseFile(), "geoip.database-file");
        assertEquals(file.displayMode(), code.displayMode(), "display.mode");
        assertEquals(file.fallbackMode(), code.fallbackMode(), "display.fallback-mode");
        assertEquals(file.unknownCode(), code.unknownCode(), "display.unknown-code");
        assertEquals(file.unknownCountry(), code.unknownCountry(), "display.unknown-country");
        assertEquals(file.unknownFlag(), code.unknownFlag(), "display.unknown-flag");
        assertEquals(file.addSpaceBeforeFlag(), code.addSpaceBeforeFlag(), "display.add-space-before-flag");
        assertEquals(file.nametagSeparator(), code.nametagSeparator(), "nametag.separator");
        assertEquals(file.glyphStartCodepoint(), code.glyphStartCodepoint(), "glyphs.start-codepoint");
        assertEquals(file.glyphMappingFile(), code.glyphMappingFile(), "glyphs.mapping-file");
        assertEquals(file.wrapMiniMessage(), code.wrapMiniMessage(), "glyphs.wrap-minimessage");
        assertEquals(file.fontKey(), code.fontKey(), "glyphs.font-key");
        assertEquals(file.luckPermsPreferenceNode(), code.luckPermsPreferenceNode(), "luckperms.preference-node");
        assertEquals(file.cacheEnabled(), code.cacheEnabled(), "cache.enabled");
        assertEquals(file.retainAfterQuitMinutes(), code.retainAfterQuitMinutes(), "cache.retain-after-quit-minutes");
        assertEquals(file.javaPackEnabled(), code.javaPackEnabled(), "resourcepack.java.enabled");
        assertEquals(file.javaPackUrl(), code.javaPackUrl(), "resourcepack.java.url");
        assertEquals(file.javaPackRequired(), code.javaPackRequired(), "resourcepack.java.required");
        assertEquals(file.javaPackPrompt(), code.javaPackPrompt(), "resourcepack.java.prompt");
        assertEquals(file.javaPackSelfHostEnabled(), code.javaPackSelfHostEnabled(), "resourcepack.java.self-host.enabled");
        assertEquals(file.javaPackSelfHostBindAddress(), code.javaPackSelfHostBindAddress(),
                "resourcepack.java.self-host.bind-address");
        assertEquals(file.javaPackSelfHostPort(), code.javaPackSelfHostPort(), "resourcepack.java.self-host.port");
        assertEquals(file.javaPackPublicAddress(), code.javaPackPublicAddress(), "resourcepack.java.self-host.public-address");
        assertEquals(file.bedrockPrefixFallbackEnabled(), code.bedrockPrefixFallbackEnabled(),
                "bedrock-detection.username-prefix-fallback.enabled");
        assertEquals(file.bedrockPrefixFallbackPrefix(), code.bedrockPrefixFallbackPrefix(),
                "bedrock-detection.username-prefix-fallback.prefix");
        assertEquals(file.debug(), code.debug(), "debug");
    }

    @Test
    void everyMessageKeyUsedInCodeIsBundled() throws Exception {
        YamlConfiguration defaults = ConfigMigratorTest.bundledDefaults();
        for (String key : new String[] {"prefix", "flag-enabled", "flag-disabled", "flag-status-on", "flag-status-off",
                "flag-usage", "flag-save-failed", "no-permission", "player-only", "player-not-found", "reload-success",
                "admin-lookup-header", "admin-lookup-player", "admin-lookup-country", "admin-lookup-code",
                "admin-lookup-visible", "status-header"}) {
            assertEquals(true, defaults.isString("messages." + key), "missing bundled message " + key);
        }
    }
}
