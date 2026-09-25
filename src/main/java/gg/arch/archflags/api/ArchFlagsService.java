package gg.arch.archflags.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * Public API surface for other Arch plugins (e.g. the planned ArchSettings GUI) to read and
 * change a player's ArchFlags state without going through chat commands.
 * <p>
 * Registered with {@link org.bukkit.plugin.ServicesManager}:
 * <pre>{@code
 * ArchFlagsService service = Bukkit.getServicesManager().load(ArchFlagsService.class);
 * }</pre>
 */
public interface ArchFlagsService {

    /**
     * Returns the resolved country for a player, if it has already been resolved and cached.
     * Never blocks and never performs a lookup -- returns {@code CountryInfo.UNKNOWN} if nothing
     * is cached yet (e.g. resolution is still in flight). Use {@link #resolveCountry(UUID)} to
     * await resolution instead.
     */
    CountryInfo getCountry(UUID uuid);

    /**
     * Same as {@link #getCountry(UUID)}, but returns a future that completes once resolution has
     * finished (immediately, if it's already cached). Never blocks the calling thread.
     */
    CompletableFuture<CountryInfo> resolveCountry(UUID uuid);

    /** Whether the player currently has their flag set to visible (default true). */
    boolean isFlagVisible(UUID uuid);

    /**
     * Sets the player's flag visibility, persisting it network-wide via the shared LuckPerms
     * store, and fires {@link gg.arch.archflags.events.ArchFlagVisibilityChangeEvent}.
     */
    void setFlagVisible(UUID uuid, boolean visible);

    /** Flips the player's current flag visibility. Equivalent to {@code /flags toggle}. */
    void toggleFlagVisibility(UUID uuid);

    static ArchFlagsService get() {
        return Bukkit.getServicesManager().load(ArchFlagsService.class);
    }
}
