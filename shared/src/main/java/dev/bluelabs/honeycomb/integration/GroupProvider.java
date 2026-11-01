package dev.bluelabs.honeycomb.integration;

import org.bukkit.entity.Player;

/**
 * Supplies a player's permission group and nametag decoration.
 *
 * <p>Backed by LuckPerms when it is installed and by a permission based fallback
 * otherwise, so servers without a permissions plugin still get meaningful
 * groups.</p>
 */
public interface GroupProvider {

    /**
     * @param player the player
     * @return the player's primary group name, or {@code null} when unknown
     */
    String group(Player player);

    /**
     * @param player the player
     * @return the group prefix, or {@code null} when the group defines none
     */
    String prefix(Player player);

    /**
     * @param player the player
     * @return the group suffix, or {@code null} when the group defines none
     */
    String suffix(Player player);

    /**
     * @param player the player
     * @return a numeric sorting weight; higher values sort first
     */
    int weight(Player player);

    /**
     * @return the name shown by {@code /honeycomb integrations}
     */
    String sourceName();
}
