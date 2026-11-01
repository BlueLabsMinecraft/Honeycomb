package dev.bluelabs.honeycomb.display;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * The information a profile condition can test.
 *
 * <p>Permission checks are delegated through a predicate so profile matching can
 * be unit tested without a running server.</p>
 */
public final class MatchContext {

    private final String worldName;
    private final String group;
    private final Predicate<String> permissionCheck;

    private MatchContext(String worldName, String group, Predicate<String> permissionCheck) {
        this.worldName = worldName == null ? "" : worldName;
        this.group = group == null ? "" : group.toLowerCase(Locale.ROOT);
        this.permissionCheck = permissionCheck;
    }

    /**
     * Builds a context for a live player.
     *
     * @param player the player
     * @param group  the player's resolved group, may be {@code null}
     * @return the context
     */
    public static MatchContext of(Player player, String group) {
        return new MatchContext(player.getWorld().getName(), group, player::hasPermission);
    }

    /**
     * Builds a context for tests and for internal synthetic checks.
     *
     * @param worldName       world name
     * @param group           group name
     * @param permissionCheck permission predicate
     * @return the context
     */
    public static MatchContext of(String worldName, String group, Predicate<String> permissionCheck) {
        return new MatchContext(worldName, group, permissionCheck);
    }

    /**
     * @return the world name, never {@code null}
     */
    public String worldName() {
        return worldName;
    }

    /**
     * @return the lower case group name, empty when unknown
     */
    public String group() {
        return group;
    }

    /**
     * @param permission permission node
     * @return true when the player has it
     */
    public boolean hasPermission(String permission) {
        return permissionCheck.test(permission);
    }
}
