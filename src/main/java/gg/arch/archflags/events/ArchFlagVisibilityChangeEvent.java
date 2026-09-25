package gg.arch.archflags.events;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired whenever a player's flag visibility preference changes, from any source (command or API).
 * Always fired synchronously on the main thread, even though the LuckPerms write that triggers it
 * may originate off-thread -- callers are responsible for hopping back to the main thread first.
 */
public class ArchFlagVisibilityChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final boolean visible;
    private final boolean previouslyVisible;

    public ArchFlagVisibilityChangeEvent(UUID playerId, boolean visible, boolean previouslyVisible) {
        super(false);
        this.playerId = playerId;
        this.visible = visible;
        this.previouslyVisible = previouslyVisible;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean wasPreviouslyVisible() {
        return previouslyVisible;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
