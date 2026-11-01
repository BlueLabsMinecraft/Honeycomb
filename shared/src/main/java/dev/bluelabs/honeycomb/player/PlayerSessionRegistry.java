package dev.bluelabs.honeycomb.player;

import dev.bluelabs.honeycomb.scheduler.TickClock;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link PlayerSession} per online player.
 *
 * <p>Sessions are removed the moment a player leaves, which is what keeps the
 * plugin memory flat on a server that runs for weeks.</p>
 */
public final class PlayerSessionRegistry {

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final TickClock clock;

    /**
     * @param clock shared tick clock
     */
    public PlayerSessionRegistry(TickClock clock) {
        this.clock = clock;
    }

    /**
     * Returns the session for a player, creating it when needed.
     *
     * @param player the player
     * @return the session
     */
    public PlayerSession get(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(),
                key -> new PlayerSession(player, clock.tick()));
    }

    /**
     * @param player the player
     * @return the session, or {@code null} when the player has none
     */
    public PlayerSession peek(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Removes a player's session.
     *
     * @param player the player who left
     * @return the removed session, or {@code null}
     */
    public PlayerSession remove(Player player) {
        return sessions.remove(player.getUniqueId());
    }

    /**
     * @return every active session
     */
    public Collection<PlayerSession> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(sessions.values()));
    }

    /**
     * @return number of tracked players
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Drops every session. Used when the plugin disables.
     */
    public void clear() {
        sessions.clear();
    }
}
