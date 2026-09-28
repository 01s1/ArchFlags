package gg.arch.archflags.api;

import gg.arch.archflags.ArchFlagsPlugin;
import gg.arch.archflags.events.ArchFlagVisibilityChangeEvent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

public final class ArchFlagsServiceImpl implements ArchFlagsService {

    private final ArchFlagsPlugin plugin;

    public ArchFlagsServiceImpl(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CountryInfo getCountry(UUID uuid) {
        return plugin.geoIpService().getCountry(uuid);
    }

    @Override
    public CompletableFuture<CountryInfo> resolveCountry(UUID uuid) {
        CountryInfo cached = plugin.geoIpService().getCountry(uuid);
        if (cached.isResolved()) {
            return CompletableFuture.completedFuture(cached);
        }
        var player = Bukkit.getPlayer(uuid);
        if (player == null || player.getAddress() == null) {
            return CompletableFuture.completedFuture(CountryInfo.UNKNOWN);
        }
        return plugin.geoIpService().resolve(uuid, player.getAddress().getAddress());
    }

    @Override
    public boolean isFlagVisible(UUID uuid) {
        return plugin.visibilityStore().isVisible(uuid);
    }

    @Override
    public void setFlagVisible(UUID uuid, boolean visible) {
        boolean previous = plugin.visibilityStore().isVisible(uuid);
        plugin.visibilityStore().setVisible(uuid, visible).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.getPluginManager().callEvent(new ArchFlagVisibilityChangeEvent(uuid, visible, previous));
                    plugin.tabIntegration().refresh(uuid);
                }));
    }

    @Override
    public void toggleFlagVisibility(UUID uuid) {
        setFlagVisible(uuid, !isFlagVisible(uuid));
    }
}
