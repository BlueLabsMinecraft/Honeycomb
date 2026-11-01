package dev.bluelabs.honeycomb.display.tablist;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.Settings;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.display.MatchContext;
import dev.bluelabs.honeycomb.display.ProfileSelector;
import dev.bluelabs.honeycomb.display.WorldProfiles;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the tablist header, footer and player list names.
 *
 * <p>Updates are diffed per player: nothing is sent when the rendered text has not
 * changed, which is what prevents the constant header flicker most tablist
 * plugins show.</p>
 */
public final class TablistService implements DisplaySystem {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final Settings settings;
    private final RenderPipeline pipeline;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final TickClock clock;
    private final ValidationReport report;

    private List<TablistProfile> profiles = new ArrayList<>();
    private WorldProfiles worldProfiles;
    private boolean enabled;
    private int serviceInterval = 40;

    /**
     * @param plugin        plugin instance
     * @param configManager configuration manager
     * @param settings      general settings
     * @param pipeline      render pipeline
     * @param sessions      player sessions
     * @param groups        permission group source
     * @param clock         shared tick clock
     * @param report        validation report
     */
    public TablistService(Plugin plugin,
                          ConfigManager configManager,
                          Settings settings,
                          RenderPipeline pipeline,
                          PlayerSessionRegistry sessions,
                          GroupProvider groups,
                          TickClock clock,
                          ValidationReport report) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.settings = settings;
        this.pipeline = pipeline;
        this.sessions = sessions;
        this.groups = groups;
        this.clock = clock;
        this.report = report;
    }

    /**
     * @param worldProfiles per-world overrides, may be {@code null}
     */
    public void setWorldProfiles(WorldProfiles worldProfiles) {
        this.worldProfiles = worldProfiles;
    }

    @Override
    public String name() {
        return "tablist";
    }

    @Override
    public void enable() {
        ConfigurationSection root = configManager.yaml(ConfigType.TABLIST).getConfigurationSection("tablist");
        this.enabled = root != null && root.getBoolean("enabled", true);
        if (!enabled) {
            this.profiles = new ArrayList<>();
            return;
        }
        int defaultInterval = root.getInt("update-interval", settings.defaultUpdateInterval());
        if (defaultInterval < 1) {
            report.warning("tablist.yml", "update-interval must be at least 1, using 40");
            defaultInterval = 40;
        }
        this.profiles = TablistProfile.loadAll(root, defaultInterval, report);
        if (!settings.staffTablist()) {
            // Staff layouts stay out of the rotation until the switch is on, so the
            // default install shows one layout to everyone.
            this.profiles.removeIf(profile -> isStaffLayout("tablist", profile.name()));
        }
        int minimum = Integer.MAX_VALUE;
        for (TablistProfile profile : profiles) {
            minimum = Math.min(minimum, profile.updateInterval());
        }
        this.serviceInterval = minimum == Integer.MAX_VALUE ? defaultInterval : minimum;
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            player.playerListName(null);
            PlayerSession session = sessions.peek(player);
            if (session != null) {
                session.tablist.header = null;
                session.tablist.footer = null;
                session.tablist.listName = null;
                session.tablist.profile = null;
            }
        }
        this.enabled = false;
    }

    @Override
    public void reload() {
        boolean wasEnabled = enabled;
        List<TablistProfile> previous = profiles;
        enable();
        if (!wasEnabled && enabled) {
            refreshAll();
            return;
        }
        if (wasEnabled && !enabled) {
            // Config turned the tablist off: clear it from every client now.
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                player.playerListName(null);
            }
            return;
        }
        if (profiles.isEmpty() && !previous.isEmpty()) {
            report.warning("tablist.yml", "Reload produced no tablist profiles, keeping the previous layout");
            this.profiles = previous;
            this.enabled = wasEnabled;
        }
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int updateInterval() {
        return serviceInterval;
    }

    @Override
    public void tick(Player player) {
        PlayerSession session = sessions.get(player);
        if (!clock.isDue(serviceInterval, session.updateOffset())) {
            return;
        }
        TablistProfile profile = select(player, session);
        if (profile == null) {
            return;
        }
        apply(player, session, profile, false);
    }

    @Override
    public void refresh(Player player) {
        if (!enabled) {
            return;
        }
        PlayerSession session = sessions.get(player);
        TablistProfile profile = select(player, session);
        if (profile != null) {
            apply(player, session, profile, true);
        }
    }

    /**
     * @param root  configuration root of the display
     * @param name  profile name
     * @return true when the profile is marked as staff only
     */
    private boolean isStaffLayout(String root, String name) {
        if ("staff".equalsIgnoreCase(name)) {
            return true;
        }
        return configManager.yaml(ConfigType.TABLIST)
                .getBoolean(root + ".profiles." + name + ".staff", false);
    }

    private TablistProfile select(Player player, PlayerSession session) {
        if (profiles.isEmpty() || !enabled) {
            return null;
        }
        if (settings.isWorldDisabled(player.getWorld().getName())) {
            return null;
        }
        if (settings.respectBypassPermission() && player.hasPermission("honeycomb.bypass")) {
            return null;
        }
        String worldName = player.getWorld().getName();
        if (worldProfiles != null) {
            if (worldProfiles.isDisabled("tablist", worldName)) {
                return null;
            }
            String forced = worldProfiles.profileName("tablist", worldName);
            if (forced != null) {
                for (TablistProfile candidate : profiles) {
                    if (candidate.name().equalsIgnoreCase(forced) && candidate.isEnabled()) {
                        session.tablist.profile = candidate.name();
                        return candidate;
                    }
                }
            }
        }
        MatchContext context = MatchContext.of(player, groups.group(player))
                .withPlaceholders(text -> pipeline.placeholders().expand(text, PlaceholderContext.of(player)));
        TablistProfile selected = ProfileSelector.select(profiles, context);
        session.tablist.profile = selected == null ? null : selected.name();
        return selected;
    }

    private void apply(Player player, PlayerSession session, TablistProfile profile, boolean force) {
        PlaceholderContext context = PlaceholderContext.of(player);

        Component header = pipeline.renderMultiline(profile.headerLines(), context);
        Component footer = pipeline.renderMultiline(profile.footerLines(), context);
        if (force || !header.equals(session.tablist.header) || !footer.equals(session.tablist.footer)) {
            player.sendPlayerListHeaderAndFooter(header, footer);
            session.tablist.header = header;
            session.tablist.footer = footer;
        }

        String listNameTemplate = profile.playerListNameTemplate();
        if (listNameTemplate == null) {
            return;
        }
        Component listName = pipeline.render(listNameTemplate, context);
        if (force || !listName.equals(session.tablist.listName)) {
            player.playerListName(listName);
            session.tablist.listName = listName;
        }
    }

    @Override
    public void forget(Player player) {
        // Nothing to release: the client drops its tablist when the player leaves.
    }

    @Override
    public String describe() {
        int layouts = profiles.size();
        return layouts + " profile(s), interval " + serviceInterval + "t";
    }

    /**
     * @return the number of loaded profiles, for diagnostics
     */
    public int profileCount() {
        return profiles.size();
    }

    /**
     * @return the plugin instance, kept for symmetry with other systems
     */
    Plugin plugin() {
        return plugin;
    }
}
