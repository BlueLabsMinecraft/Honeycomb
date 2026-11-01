package dev.bluelabs.honeycomb.integration;

import org.bukkit.entity.Player;

import java.text.DecimalFormat;

/**
 * Economy provider used when Vault or an economy plugin is missing. It reports
 * unavailable so {@code %econ_balance%} stays unresolved instead of showing zero.
 */
public final class EmptyEconomyProvider implements EconomyProvider {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00");

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public double balance(Player player) {
        return Double.NaN;
    }

    @Override
    public String format(double amount) {
        return FORMAT.format(amount);
    }

    @Override
    public String sourceName() {
        return "";
    }
}
