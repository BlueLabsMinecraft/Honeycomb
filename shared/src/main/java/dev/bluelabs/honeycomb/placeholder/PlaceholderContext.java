package dev.bluelabs.honeycomb.placeholder;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a placeholder resolver may need: the player it is rendered for, the
 * world being displayed and any extra values a caller wants available.
 *
 * <p>The player may be {@code null} for server-wide renders such as console
 * output, which is why every built-in resolver checks for it.</p>
 */
public final class PlaceholderContext {

    private final Player player;
    private final String worldName;
    private final Map<String, String> extras;

    private PlaceholderContext(Player player, String worldName, Map<String, String> extras) {
        this.player = player;
        this.worldName = worldName;
        this.extras = extras;
    }

    /**
     * Context for a player, using the world the player is currently in.
     *
     * @param player the player
     * @return the context
     */
    public static PlaceholderContext of(Player player) {
        String world = player == null || player.getWorld() == null ? "" : player.getWorld().getName();
        return new PlaceholderContext(player, world, Map.of());
    }

    /**
     * Context with no player, for server placeholders.
     *
     * @param worldName world name used by world placeholders, may be empty
     * @return the context
     */
    public static PlaceholderContext global(String worldName) {
        return new PlaceholderContext(null, worldName == null ? "" : worldName, Map.of());
    }

    /**
     * @return the player, or {@code null} for server contexts
     */
    public Player player() {
        return player;
    }

    /**
     * @return the world name this render belongs to
     */
    public String worldName() {
        return worldName;
    }

    /**
     * Reads an extra value added with {@link #with(String, String)}.
     *
     * @param key extra key
     * @return the value, or {@code null}
     */
    public String extra(String key) {
        return extras.get(key);
    }

    /**
     * @return an immutable view of the extra values
     */
    public Map<String, String> extras() {
        return Collections.unmodifiableMap(extras);
    }

    /**
     * @param key   extra key
     * @param value extra value
     * @return a new context with the value added
     */
    public PlaceholderContext with(String key, String value) {
        Map<String, String> copy = new HashMap<>(extras);
        copy.put(key, value);
        return new PlaceholderContext(player, worldName, Map.copyOf(copy));
    }

    /**
     * Stable key for cache lookups: identical contexts must produce identical
     * keys, and different players must never share a cache entry.
     *
     * @return the cache key
     */
    public String cacheKey() {
        UUID id = player == null ? null : player.getUniqueId();
        StringBuilder builder = new StringBuilder(32);
        builder.append(id == null ? "server" : id.toString());
        builder.append('|').append(worldName);
        if (!extras.isEmpty()) {
            builder.append('|').append(extras);
        }
        return builder.toString();
    }
}
