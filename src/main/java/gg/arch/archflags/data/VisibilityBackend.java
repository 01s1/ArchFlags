package gg.arch.archflags.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where flag visibility is persisted. Kept free of LuckPerms types so {@link FlagVisibilityStore} loads on
 * servers without LuckPerms; the only implementation is {@link LuckPermsVisibility}.
 */
interface VisibilityBackend {

    /** The stored value for a loaded (online) player, or null when the player is not loaded. */
    Boolean visibleIfLoaded(UUID uuid);

    /** Works for offline players too. */
    CompletableFuture<Boolean> visibleAsync(UUID uuid);

    /** Completes once the value is saved. */
    CompletableFuture<Void> setVisible(UUID uuid, boolean visible);
}
