package dev.bluelabs.honeycomb.display;

import org.bukkit.entity.Player;

/**
 * One visual element Honeycomb controls, such as the tablist or the sidebar.
 *
 * <p>The coordinator owns the lifecycle: systems are never scheduled directly and
 * never create their own repeating tasks.</p>
 */
public interface DisplaySystem {

    /**
     * @return stable internal name, also used by {@code /honeycomb status}
     */
    String name();

    /**
     * Reads configuration and starts the system.
     */
    void enable();

    /**
     * Stops the system and removes everything it placed on player clients.
     */
    void disable();

    /**
     * Re-reads configuration and re-applies the system to every online player.
     */
    void reload();

    /**
     * @return true when this system is currently active
     */
    boolean isEnabled();

    /**
     * @return how often, in ticks, {@link #tick(Player)} should run
     */
    int updateInterval();

    /**
     * Updates one player's display. Called only when the player is due.
     *
     * @param player the player to update
     */
    void tick(Player player);

    /**
     * Re-applies the display to a single player immediately.
     *
     * @param player the player
     */
    void refresh(Player player);

    /**
     * Removes all state belonging to a player who left the server.
     *
     * @param player the player who quit
     */
    void forget(Player player);

    /**
     * @return a short human readable description for status output
     */
    String describe();
}
