package gg.arch.archflags.config;

import gg.arch.archflags.ArchFlagsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, validated snapshot of config.yml. Rebuilt on every reload so nothing holds a stale
 * reference to the underlying FileConfiguration.
 */
public final class ArchFlagsConfig {

    private final boolean debug;

    private final String geoipDatabaseFile;

    private final DisplayMode displayMode;
    private final DisplayMode fallbackMode;
    private final String unknownCode;
    private final String unknownCountry;
    private final String unknownFlag;
    private final boolean addSpaceBeforeFlag;

    private final String nametagSeparator;

    private final int glyphStartCodepoint;
    private final String glyphMappingFile;
    private final boolean wrapMiniMessage;
    private final String fontKey;

    private final String luckPermsPreferenceNode;

    private final boolean cacheEnabled;
    private final int retainAfterQuitMinutes;

    private final boolean javaPackEnabled;
    private final String javaPackUrl;
    private final boolean javaPackRequired;
    private final String javaPackPrompt;
    private final boolean javaPackSelfHostEnabled;
    private final String javaPackSelfHostBindAddress;
    private final int javaPackSelfHostPort;
    private final String javaPackPublicAddress;

    private final java.util.Map<String, String> messages = new java.util.HashMap<>();

    public ArchFlagsConfig(ArchFlagsPlugin plugin, FileConfiguration cfg) {
        this.debug = cfg.getBoolean("debug", false);

        this.geoipDatabaseFile = cfg.getString("geoip.database-file", "GeoLite2-Country.mmdb");

        this.displayMode = DisplayMode.parse(cfg.getString("display.mode"), DisplayMode.CUSTOM_GLYPH);
        this.fallbackMode = DisplayMode.parse(cfg.getString("display.fallback-mode"), DisplayMode.COUNTRY_CODE);
        this.unknownCode = cfg.getString("display.unknown-code", "UN");
        this.unknownCountry = cfg.getString("display.unknown-country", "Unknown");
        this.unknownFlag = cfg.getString("display.unknown-flag", "");
        this.addSpaceBeforeFlag = cfg.getBoolean("display.add-space-before-flag", true);

        this.nametagSeparator = cfg.getString("nametag.separator", " | ");

        String startHex = cfg.getString("glyphs.start-codepoint", "E200");
        int parsedStart;
        try {
            parsedStart = Integer.parseInt(startHex.trim(), 16);
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("glyphs.start-codepoint '" + startHex + "' is not valid hex; defaulting to E200.");
            parsedStart = 0xE200;
        }
        this.glyphStartCodepoint = parsedStart;
        this.glyphMappingFile = cfg.getString("glyphs.mapping-file", "glyph-mapping.yml");
        this.wrapMiniMessage = cfg.getBoolean("glyphs.wrap-minimessage", true);
        this.fontKey = cfg.getString("glyphs.font-key", "archflags:flags");

        this.luckPermsPreferenceNode = cfg.getString("luckperms.preference-node", "archflags.visible");

        this.cacheEnabled = cfg.getBoolean("cache.enabled", true);
        this.retainAfterQuitMinutes = Math.max(1, cfg.getInt("cache.retain-after-quit-minutes", 30));

        this.javaPackEnabled = cfg.getBoolean("resourcepack.java.enabled", true);
        this.javaPackUrl = cfg.getString("resourcepack.java.url", "");
        this.javaPackRequired = cfg.getBoolean("resourcepack.java.required", false);
        this.javaPackPrompt = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("resourcepack.java.prompt", "&bArchFlags needs its small flag pack alongside your other resource packs."));
        this.javaPackSelfHostEnabled = cfg.getBoolean("resourcepack.java.self-host.enabled", true);
        this.javaPackSelfHostBindAddress = cfg.getString("resourcepack.java.self-host.bind-address", "0.0.0.0");
        this.javaPackSelfHostPort = cfg.getInt("resourcepack.java.self-host.port", 25566);
        this.javaPackPublicAddress = cfg.getString("resourcepack.java.self-host.public-address", "");

        if (cfg.isConfigurationSection("messages")) {
            for (String key : cfg.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, ChatColor.translateAlternateColorCodes('&', cfg.getString("messages." + key, "")));
            }
        }
    }

    public boolean debug() {
        return debug;
    }

    public String geoipDatabaseFile() {
        return geoipDatabaseFile;
    }

    public DisplayMode displayMode() {
        return displayMode;
    }

    public DisplayMode fallbackMode() {
        return fallbackMode;
    }

    public String unknownCode() {
        return unknownCode;
    }

    public String unknownCountry() {
        return unknownCountry;
    }

    public String unknownFlag() {
        return unknownFlag;
    }

    public boolean addSpaceBeforeFlag() {
        return addSpaceBeforeFlag;
    }

    public String nametagSeparator() {
        return nametagSeparator;
    }

    public int glyphStartCodepoint() {
        return glyphStartCodepoint;
    }

    public String glyphMappingFile() {
        return glyphMappingFile;
    }

    public boolean wrapMiniMessage() {
        return wrapMiniMessage;
    }

    public String fontKey() {
        return fontKey;
    }

    public String luckPermsPreferenceNode() {
        return luckPermsPreferenceNode;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    public int retainAfterQuitMinutes() {
        return retainAfterQuitMinutes;
    }

    public boolean javaPackEnabled() {
        return javaPackEnabled;
    }

    public String javaPackUrl() {
        return javaPackUrl;
    }

    public boolean javaPackRequired() {
        return javaPackRequired;
    }

    public String javaPackPrompt() {
        return javaPackPrompt;
    }

    public boolean javaPackSelfHostEnabled() {
        return javaPackSelfHostEnabled;
    }

    public String javaPackSelfHostBindAddress() {
        return javaPackSelfHostBindAddress;
    }

    public int javaPackSelfHostPort() {
        return javaPackSelfHostPort;
    }

    public String javaPackPublicAddress() {
        return javaPackPublicAddress;
    }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }

    public String prefixedMessage(String key) {
        return message("prefix") + message(key);
    }
}
