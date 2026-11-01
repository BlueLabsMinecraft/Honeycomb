package dev.bluelabs.honeycomb.integration;

import org.bukkit.entity.Player;

/**
 * Supplies player state owned by other plugins (AFK, nicknames, vanish).
 *
 * <p>Implementations answer "unknown" rather than guessing: inventing an AFK flag
 * or nickname would be worse than showing nothing.</p>
 */
public interface PlayerStateProvider {

    /**
     * @param player the player
     * @return true when the player is marked as AFK
     */
    boolean afk(Player player);

    /**
     * @param player the player
     * @return the player's nickname, or {@code null} when they have none
     */
    String nickname(Player player);

    /**
     * @param player the player
     * @return true when the player is vanished
     */
    boolean vanished(Player player);

    /**
     * @return which plugins are answering, for diagnostics
     */
    String sourceName();
}
