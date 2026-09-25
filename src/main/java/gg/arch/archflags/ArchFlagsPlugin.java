package gg.arch.archflags;

import gg.arch.archflags.api.ArchFlagsService;
import gg.arch.archflags.api.ArchFlagsServiceImpl;
import gg.arch.archflags.commands.ArchFlagsAdminCommand;
import gg.arch.archflags.commands.FlagsCommand;
import gg.arch.archflags.config.ArchFlagsConfig;
import gg.arch.archflags.data.FlagVisibilityStore;
import gg.arch.archflags.geo.GeoIpService;
import gg.arch.archflags.glyph.GlyphMapping;
import gg.arch.archflags.listeners.PlayerConnectionListener;
import gg.arch.archflags.placeholder.ArchFlagsExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArchFlagsPlugin extends JavaPlugin {

    private volatile ArchFlagsConfig config;
    private GeoIpService geoIpService;
    private FlagVisibilityStore visibilityStore;
    private GlyphMapping glyphMapping;
    private ArchFlagsExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("glyph-mapping.yml", false);

        this.config = new ArchFlagsConfig(this, getConfig());
        this.geoIpService = new GeoIpService(this);
        this.visibilityStore = new FlagVisibilityStore(this);
        this.glyphMapping = new GlyphMapping();

        glyphMapping.load(this, config.glyphMappingFile());
        geoIpService.loadDatabaseAsync();
        visibilityStore.hookLuckPerms();
        if (!visibilityStore.isLuckPermsAvailable()) {
            getLogger().warning("LuckPerms not found -- flag visibility will default to per-server memory only (not persisted network-wide) until LuckPerms is installed.");
        }

        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        var flagsCommand = getCommand("flags");
        if (flagsCommand != null) {
            flagsCommand.setExecutor(new FlagsCommand(this));
        }
        var adminCommand = getCommand("archflags");
        if (adminCommand != null) {
            adminCommand.setExecutor(new ArchFlagsAdminCommand(this));
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
        if (geoIpService != null) {
            geoIpService.shutdown();
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

    /** Reloads config.yml and the glyph mapping, and re-attempts to open the GeoIP database. */
    public void reload() {
        reloadConfig();
        this.config = new ArchFlagsConfig(this, getConfig());
        glyphMapping.load(this, config.glyphMappingFile());
        geoIpService.loadDatabaseAsync();
        visibilityStore.hookLuckPerms();
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
}
