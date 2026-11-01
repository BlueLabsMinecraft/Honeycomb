package dev.bluelabs.honeycomb.integration;

import org.bukkit.entity.Player;

/**
 * Optional economy access used by the {@code econ_balance} placeholder.
 *
 * <p>Without an economy plugin the provider reports unavailable and the
 * placeholder stays unresolved instead of showing a fake balance.</p>
 */
public interface EconomyProvider {

    /**
     * @return true when an economy backend is present and usable
     */
    boolean available();

    /**
     * @param player the player
     * @return the balance, or {@link Double#NaN} when it cannot be read
     */
    double balance(Player player);

    /**
     * @param amount the amount to format
     * @return the formatted amount
     */
    String format(double amount);

    /**
     * @return the provider name for diagnostics
     */
    String sourceName();
}
