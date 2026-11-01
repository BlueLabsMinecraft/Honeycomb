package dev.bluelabs.honeycomb.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;

/**
 * Economy lookups through Vault.
 *
 * <p>Vault itself needs an economy plugin behind it, so availability is decided by
 * whether a provider is actually registered.</p>
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private static final DecimalFormat FALLBACK_FORMAT = new DecimalFormat("#,##0.00");

    private final Economy economy;

    /**
     * @param economy the Vault economy provider
     */
    public VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    /**
     * Creates a provider when Vault and an economy plugin are both present.
     *
     * @return the provider, or {@code null} when unavailable
     */
    public static VaultEconomyProvider create() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return null;
        }
        return new VaultEconomyProvider(registration.getProvider());
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public double balance(Player player) {
        if (economy == null) {
            return Double.NaN;
        }
        try {
            return economy.getBalance(player);
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }

    @Override
    public String format(double amount) {
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (RuntimeException ignored) {
                // Fall through to the generic format below.
            }
        }
        return FALLBACK_FORMAT.format(amount);
    }

    @Override
    public String sourceName() {
        return economy == null ? "Unavailable" : economy.getName();
    }
}
