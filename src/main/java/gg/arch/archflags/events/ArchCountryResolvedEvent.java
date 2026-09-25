package gg.arch.archflags.events;

import gg.arch.archflags.api.CountryInfo;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once a player's country has been resolved (or resolution has failed/fallen back to
 * unknown) and cached. Always fired synchronously on the main thread.
 */
public class ArchCountryResolvedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final CountryInfo countryInfo;

    public ArchCountryResolvedEvent(UUID playerId, CountryInfo countryInfo) {
        super(false);
        this.playerId = playerId;
        this.countryInfo = countryInfo;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public CountryInfo getCountryInfo() {
        return countryInfo;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
