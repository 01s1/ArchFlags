package gg.arch.archflags;

import gg.arch.archflags.api.ArchFlagsService;
import gg.arch.archflags.api.ArchFlagsServiceImpl;
import gg.arch.archflags.commands.ArchFlagsAdminCommand;
import gg.arch.archflags.commands.FlagsCommand;
import gg.arch.archflags.config.ArchFlagsConfig;
import gg.arch.archflags.config.ConfigMigrator;
import gg.arch.archflags.data.FlagVisibilityStore;
import gg.arch.archflags.geo.GeoIpService;
import gg.arch.archflags.glyph.GlyphMapping;
import gg.arch.archflags.integration.FloodgateIntegration;
import gg.arch.archflags.integration.TabIntegration;
import gg.arch.archflags.listeners.PlayerConnectionListener;
import gg.arch.archflags.placeholder.ArchFlagsExpansion;
import gg.arch.archflags.resourcepack.JavaResourcePackService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArchFlagsPlugin extends JavaPlugin {

    private volatile ArchFlagsConfig config;
    private GeoIpService geoIpService;
    private FlagVisibilityStore visibilityStore;
    private GlyphMapping glyphMapping;
    private ArchFlagsExpansion expansion;
    private TabIntegration tabIntegration;
    private FloodgateIntegration floodgateIntegration;
    private JavaResourcePackService resourcePackService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        reloadConfig();
        // saveResource(..., false) logs a warning on every start once the file exists.
        if (!new File(getDataFolder(), "glyph-mapping.yml").exists()) {
            saveResource("glyph-mapping.yml", false);
        }

        this.config = new ArchFlagsConfig(this, getConfig());
        this.geoIpService = new GeoIpService(this);
        this.visibilityStore = new FlagVisibilityStore(this);
        this.glyphMapping = new GlyphMapping();
        this.tabIntegration = new TabIntegration(this);
        this.floodgateIntegration = new FloodgateIntegration(this);
        this.resourcePackService = new JavaResourcePackService(this);

        glyphMapping.load(this, config.glyphMappingFile());
        geoIpService.loadDatabaseAsync();
        visibilityStore.hookLuckPerms();
        if (!visibilityStore.isLuckPermsAvailable()) {
            getLogger().warning("LuckPerms not found -- flag visibility will default to per-server memory only (not persisted network-wide) until LuckPerms is installed.");
        }
        tabIntegration.hook();
        floodgateIntegration.hook();
        resourcePackService.reload();

        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        var flagCommand = getCommand("flag");
        if (flagCommand != null) {
            var flagsCommand = new FlagsCommand(this);
            flagCommand.setExecutor(flagsCommand);
            flagCommand.setTabCompleter(flagsCommand);
        }
        var adminCommand = getCommand("archflags");
        if (adminCommand != null) {
            var archFlagsAdminCommand = new ArchFlagsAdminCommand(this);
            adminCommand.setExecutor(archFlagsAdminCommand);
            adminCommand.setTabCompleter(archFlagsAdminCommand);
        }

        Bukkit.getServicesManager().register(ArchFlagsService.class, new ArchFlagsServiceImpl(this), this, ServicePriority.Normal);

        registerPlaceholderExpansion();

        getLogger().info("ArchFlags enabled. Mode=" + config.displayMode() + ", glyphs=" + glyphMapping.size()
                + ", GeoIP=" + (geoIpService.isAvailable() ? "loaded" : "pending/missing") + ".");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (resourcePackService != null) {
            resourcePackService.stop();
        }
        if (geoIpService != null) {
            geoIpService.shutdown();
        }
    }

    /** Adds keys introduced since the file was written (backup first); never changes existing values. */
    private void migrateConfig() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigMigrator.migrate(new File(getDataFolder(), "config.yml"), defaults, getLogger());
        } catch (IOException | InvalidConfigurationException ex) {
            getLogger().warning("Could not migrate config.yml (" + ex.getMessage()
                    + "); keys missing from it fall back to the built-in defaults.");
        }
    }

    private void registerPlaceholderExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found -- ArchFlags placeholders will not be available until it's installed.");
            return;
        }
        expansion = new ArchFlagsExpansion(this);
        expansion.register();
        getLogger().info("Registered PlaceholderAPI expansion 'archflags'.");
    }

    /** Reloads config.yml, the glyph mapping, TAB/Floodgate hooks, and the Java resource pack. */
    public void reload() {
        migrateConfig();
        reloadConfig();
        this.config = new ArchFlagsConfig(this, getConfig());
        glyphMapping.load(this, config.glyphMappingFile());
        geoIpService.loadDatabaseAsync();
        visibilityStore.hookLuckPerms();
        tabIntegration.hook();
        floodgateIntegration.hook();
        resourcePackService.reload();
    }

    public ArchFlagsConfig archConfig() {
        return config;
    }

    public GeoIpService geoIpService() {
        return geoIpService;
    }

    public FlagVisibilityStore visibilityStore() {
        return visibilityStore;
    }

    public GlyphMapping glyphMapping() {
        return glyphMapping;
    }

    public TabIntegration tabIntegration() {
        return tabIntegration;
    }

    public FloodgateIntegration floodgateIntegration() {
        return floodgateIntegration;
    }

    public JavaResourcePackService resourcePackService() {
        return resourcePackService;
    }
}
