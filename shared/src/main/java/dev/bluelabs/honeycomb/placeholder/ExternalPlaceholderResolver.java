package dev.bluelabs.honeycomb.placeholder;

import org.bukkit.entity.Player;

/**
 * Bridge to an external placeholder provider (PlaceholderAPI today, another
 * provider tomorrow) without making the placeholder engine depend on it.
 */
@FunctionalInterface
public interface ExternalPlaceholderResolver {

    /**
     * Replaces every placeholder this provider understands.
     *
     * @param player the player context, may be {@code null}
     * @param input  text containing placeholders
     * @return the text with known placeholders replaced, unknown ones left alone
     */
    String resolve(Player player, String input);
}
