package dev.bluelabs.honeycomb.integration;

import dev.bluelabs.honeycomb.placeholder.ExternalPlaceholderResolver;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Hands unresolved text to PlaceholderAPI.
 *
 * <p>Only instantiated when PlaceholderAPI is installed, which is why the class can
 * import its API directly without making it a hard dependency.</p>
 */
public final class PlaceholderApiBridge implements ExternalPlaceholderResolver {

    @Override
    public String resolve(Player player, String input) {
        if (player == null) {
            return PlaceholderAPI.setPlaceholders(null, input);
        }
        return PlaceholderAPI.setPlaceholders(player, input);
    }
}
