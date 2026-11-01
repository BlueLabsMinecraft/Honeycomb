package dev.bluelabs.honeycomb.integration;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.placeholder.ExternalPlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects optional plugins and wires the providers Honeycomb uses.
 *
 * <p>Every integration is isolated: a plugin that is missing, disabled, or whose
 * API throws falls back to a neutral provider and is reported in
 * {@code /honeycomb integrations}. Nothing here is required for the plugin to
 * start.</p>
 */
public final class IntegrationManager {

    /**
     * Result of one integration check.
     *
     * @param name      integration name
     * @param available whether it is usable
     * @param detail    extra information for the status command
     */
    public record Status(String name, boolean available, String detail) {
    }

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ValidationReport report;
    private final List<Status> statuses = new ArrayList<>();

    private GroupProvider groups;
    private PlayerStateProvider states;
    private EconomyProvider economy;
    private ExternalPlaceholderResolver placeholderBridge;

    /**
     * @param plugin        plugin instance
     * @param configManager configuration manager
     * @param report        validation report
     */
    public IntegrationManager(Plugin plugin, ConfigManager configManager, ValidationReport report) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.report = report;
    }

    /**
     * Re-runs detection and rebuilds every provider.
     */
    public void detect() {
        statuses.clear();
        ConfigurationSection root = configManager.yaml(ConfigType.INTEGRATIONS).getConfigurationSection("integrations");

        this.groups = buildGroups(root);
        this.states = buildStates(root);
        this.economy = buildEconomy(root);
        this.placeholderBridge = buildPlaceholderBridge(root);

        reportOptional("Multiverse-Core", "Per-world layouts use world names from worlds/");
        reportOptional("DiscordSRV", "Detected only; no placeholders are read from it");
        reportOptional("ProtocolLib", "Not required by Honeycomb");
        detectDisplayPlugins();
    }

    private GroupProvider buildGroups(ConfigurationSection root) {
        boolean enabled = bool(root, "luckperms.enabled", true);
        boolean usePrefix = bool(root, "luckperms.use-prefix", true);
        boolean useSuffix = bool(root, "luckperms.use-suffix", true);
        if (enabled && present("LuckPerms")) {
            try {
                GroupProvider provider = new LuckPermsGroupProvider(usePrefix, useSuffix);
                statuses.add(new Status("LuckPerms", true, "groups, prefixes and weights"));
                return provider;
            } catch (Throwable throwable) {
                report.warning("integrations.yml", "LuckPerms is installed but its API could not be used ("
                        + throwable.getClass().getSimpleName() + "); falling back to permissions");
            }
        }
        statuses.add(new Status("LuckPerms", false, enabled
                ? "not installed, using permission groups"
                : "disabled in integrations.yml, using permission groups"));
        return new PermissionGroupProvider(configManager);
    }

    private PlayerStateProvider buildStates(ConfigurationSection root) {
        Set<String> afkKeys = new LinkedHashSet<>(lowerCaseList(root, "state.afk-metadata-keys"));
        Set<String> vanishKeys = new LinkedHashSet<>(lowerCaseList(root, "state.vanish-metadata-keys"));
        if (afkKeys.isEmpty()) {
            afkKeys.add("afk");
            afkKeys.add("cmi.afk");
        }
        if (vanishKeys.isEmpty()) {
            vanishKeys.add("vanished");
            vanishKeys.add("invisible");
        }
        boolean nicknames = bool(root, "state.use-display-name-as-nickname", true);

        List<String> sources = new ArrayList<>();
        if (bool(root, "essentials.enabled", true) && (present("Essentials") || present("EssentialsX"))) {
            sources.add("EssentialsX");
        }
        if (bool(root, "cmi.enabled", true) && present("CMI")) {
            sources.add("CMI");
        }
        boolean vanishPlugin = present("SuperVanish") || present("PremiumVanish");
        if (bool(root, "vanish.enabled", true) && vanishPlugin) {
            sources.add(present("PremiumVanish") ? "PremiumVanish" : "SuperVanish");
        }
        statuses.add(new Status("EssentialsX", present("Essentials") || present("EssentialsX"),
                present("Essentials") || present("EssentialsX") ? "AFK and nickname metadata" : "not installed"));
        statuses.add(new Status("CMI", present("CMI"), present("CMI") ? "AFK metadata" : "not installed"));
        statuses.add(new Status("Vanish", vanishPlugin,
                vanishPlugin ? "vanish metadata is respected" : "no vanish plugin detected"));

        if (sources.isEmpty()) {
            return new MetadataStateProvider(Set.of(), vanishKeys, nicknames, "Metadata only");
        }
        return new MetadataStateProvider(afkKeys, vanishKeys, nicknames, String.join(", ", sources));
    }

    private EconomyProvider buildEconomy(ConfigurationSection root) {
        boolean enabled = bool(root, "vault.enabled", true);
        if (enabled && present("Vault")) {
            VaultEconomyProvider provider = VaultEconomyProvider.create();
            if (provider != null) {
                statuses.add(new Status("Vault", true, provider.sourceName()));
                return provider;
            }
            statuses.add(new Status("Vault", false, "installed, but no economy plugin registered"));
            return new EmptyEconomyProvider();
        }
        statuses.add(new Status("Vault", false, enabled ? "not installed" : "disabled in integrations.yml"));
        return new EmptyEconomyProvider();
    }

    private ExternalPlaceholderResolver buildPlaceholderBridge(ConfigurationSection root) {
        boolean enabled = bool(root, "placeholderapi.enabled", true);
        if (!enabled) {
            statuses.add(new Status("PlaceholderAPI", false, "disabled in integrations.yml"));
            return null;
        }
        if (!present("PlaceholderAPI")) {
            statuses.add(new Status("PlaceholderAPI", false, "not installed"));
            return null;
        }
        try {
            ExternalPlaceholderResolver bridge = new PlaceholderApiBridge();
            statuses.add(new Status("PlaceholderAPI", true, "external placeholders supported"));
            return bridge;
        } catch (Throwable throwable) {
            report.warning("integrations.yml", "PlaceholderAPI could not be hooked: "
                    + throwable.getClass().getSimpleName());
            statuses.add(new Status("PlaceholderAPI", false, "hook failed"));
            return null;
        }
    }

    private void detectDisplayPlugins() {
        List<String> conflicts = new ArrayList<>();
        for (String name : List.of("TAB", "scoreboard-revision", "FeatherBoard", "NametagEdit")) {
            if (present(name)) {
                conflicts.add(name);
            }
        }
        if (conflicts.isEmpty()) {
            statuses.add(new Status("Display plugins", false, "no competing display plugin detected"));
            return;
        }
        statuses.add(new Status("Display plugins", true, String.join(", ", conflicts)));
        if (bool(configManager.yaml(ConfigType.CONFIG).getConfigurationSection("conflicts"),
                "log-advice", true)) {
            report.warning("conflicts", String.join(", ", conflicts)
                    + " also manage player displays. Disable the same element there (tablist, sidebar, "
                    + "nametags, bossbars) or here, otherwise the last plugin to update wins");
        }
    }

    private void reportOptional(String pluginName, String detail) {
        statuses.add(new Status(pluginName, present(pluginName), present(pluginName) ? detail : "not installed"));
    }

    private boolean bool(ConfigurationSection root, String path, boolean fallback) {
        return root == null ? fallback : root.getBoolean(path, fallback);
    }

    private List<String> lowerCaseList(ConfigurationSection root, String path) {
        List<String> values = new ArrayList<>();
        if (root == null) {
            return values;
        }
        for (String entry : root.getStringList(path)) {
            if (entry != null && !entry.isBlank()) {
                values.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private boolean present(String pluginName) {
        var manager = Bukkit.getPluginManager();
        return manager.isPluginEnabled(pluginName) || manager.getPlugin(pluginName) != null;
    }

    /**
     * @return the active group provider
     */
    public GroupProvider groups() {
        return groups;
    }

    /**
     * @return the active AFK, nickname and vanish provider
     */
    public PlayerStateProvider states() {
        return states;
    }

    /**
     * @return the active economy provider
     */
    public EconomyProvider economy() {
        return economy;
    }

    /**
     * @return the PlaceholderAPI bridge, or {@code null} when unavailable
     */
    public ExternalPlaceholderResolver placeholderBridge() {
        return placeholderBridge;
    }

    /**
     * @return every integration status, in detection order
     */
    public List<Status> statuses() {
        return List.copyOf(statuses);
    }

    /**
     * @return the plugin instance
     */
    public Plugin plugin() {
        return plugin;
    }
}
