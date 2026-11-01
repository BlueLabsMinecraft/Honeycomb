package dev.bluelabs.honeycomb.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when Honeycomb re-applies its displays to a player, either because the
 * player joined, changed world or asked for a refresh.
 */
public final class PlayerDisplayRefreshEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    /**
     * @param player the player whose displays were refreshed
     */
    public PlayerDisplayRefreshEvent(Player player) {
        this.player = player;
    }

    /**
     * @return the player
     */
    public Player player() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return the handler list, required by Bukkit
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
