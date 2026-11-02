package dev.bluelabs.honeycomb.display.scoreboard;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.Settings;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.BoardAccess;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.display.MatchContext;
import dev.bluelabs.honeycomb.display.PlayerBoard;
import dev.bluelabs.honeycomb.display.ProfileSelector;
import dev.bluelabs.honeycomb.display.WorldProfiles;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import dev.bluelabs.honeycomb.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the sidebar scoreboard.
 *
 * <p>Each player gets a private board, rows are updated individually and only when
 * their text changes, and the objective is never recreated while it is on screen.
 * That combination is what keeps the sidebar flicker free and keeps other
 * plugins' scoreboards untouched.</p>
 */
public final class ScoreboardService implements DisplaySystem {

    private final ConfigManager configManager;
    private final Settings settings;
    private final RenderPipeline pipeline;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final TickClock clock;
    private final ValidationReport report;

    private List<ScoreboardProfile> profiles = new ArrayList<>();
    private WorldProfiles worldProfiles;
    private boolean enabled;
    private int serviceInterval = 20;

    /**
     * @param configManager configuration manager
     * @param settings      general settings
     * @param pipeline      render pipeline
     * @param sessions      player sessions
     * @param groups        permission group source
     * @param clock         shared tick clock
     * @param report        validation report
     */
    public ScoreboardService(ConfigManager configManager,
                             Settings settings,
                             RenderPipeline pipeline,
                             PlayerSessionRegistry sessions,
                             GroupProvider groups,
                             TickClock clock,
                             ValidationReport report) {
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
        return "scoreboard";
    }

    /**
     * Reports once whether this server can hand out a private scoreboard. Folia is
     * the reason this exists: the answer decides which drawing path is available.
     */
    private void probeScoreboardSupport() {
        try {
            org.bukkit.scoreboard.Scoreboard probe =
                    org.bukkit.Bukkit.getScoreboardManager().getNewScoreboard();
            probe.registerNewObjective("honeycomb-probe", org.bukkit.scoreboard.Criteria.DUMMY,
                    net.kyori.adventure.text.Component.empty());
            org.bukkit.Bukkit.getLogger().info("[Honeycomb] Scoreboard support: private boards work");
        } catch (Throwable failure) {
            org.bukkit.Bukkit.getLogger().info("[Honeycomb] Scoreboard support: no private boards on this "
                    + "server, the scoreboard is sent as packets instead");
        }
    }

    @Override
    public void enable() {
        probeScoreboardSupport();
        ConfigurationSection root = configManager.yaml(ConfigType.SCOREBOARDS).getConfigurationSection("scoreboards");
        this.enabled = root != null && root.getBoolean("enabled", true);
        if (!enabled) {
            this.profiles = new ArrayList<>();
            return;
        }
        int defaultInterval = root.getInt("update-interval", settings.defaultUpdateInterval());
        if (defaultInterval < 1) {
            report.warning("scoreboards.yml", "update-interval must be at least 1, using 20");
            defaultInterval = 20;
        }
        this.profiles = ScoreboardProfile.loadAll(root, defaultInterval, report);
        if (!settings.staffScoreboard()) {
            this.profiles.removeIf(profile -> isStaffLayout(profile.name()));
        }
        int minimum = Integer.MAX_VALUE;
        for (ScoreboardProfile profile : profiles) {
            minimum = Math.min(minimum, profile.updateInterval());
        }
        this.serviceInterval = minimum == Integer.MAX_VALUE ? defaultInterval : minimum;
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.peek(player);
            if (session == null) {
                continue;
            }
            clear(player, session);
        }
        this.enabled = false;
    }

    @Override
    public void reload() {
        enable();
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
        if (!enabled) {
            return;
        }
        PlayerSession session = sessions.get(player);
        if (!clock.isDue(serviceInterval, session.updateOffset())) {
            return;
        }
        ScoreboardProfile profile = select(player);
        if (profile == null) {
            if (session.sidebar.profile != null) {
                clear(player, session);
            }
            return;
        }
        apply(player, session, profile);
    }

    @Override
    public void refresh(Player player) {
        if (!enabled) {
            return;
        }
        PlayerSession session = sessions.get(player);
        ScoreboardProfile profile = select(player);
        if (profile == null) {
            clear(player, session);
            return;
        }
        apply(player, session, profile);
    }

    /**
     * @param name profile name
     * @return true when the profile is marked as staff only
     */
    private boolean isStaffLayout(String name) {
        if ("staff".equalsIgnoreCase(name)) {
            return true;
        }
        return configManager.yaml(ConfigType.SCOREBOARDS)
                .getBoolean("scoreboards." + name + ".staff", false);
    }

    private ScoreboardProfile select(Player player) {
        if (profiles.isEmpty()) {
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
            if (worldProfiles.isDisabled("scoreboard", worldName)) {
                return null;
            }
            String forced = worldProfiles.profileName("scoreboard", worldName);
            if (forced != null) {
                for (ScoreboardProfile candidate : profiles) {
                    if (candidate.name().equalsIgnoreCase(forced) && candidate.isEnabled()) {
                        return candidate;
                    }
                }
            }
        }
        ScoreboardProfile chosen = ProfileSelector.select(profiles, MatchContext.of(player, groups.group(player))
                .withPlaceholders(text -> pipeline.placeholders().expand(text, PlaceholderContext.of(player))));
        if (chosen != null && chosen.isEnabled() && !isStaffLayout(chosen.name())) {
            return chosen;
        }
        if (chosen != null && chosen.isEnabled() && player.hasPermission("honeycomb.staff")) {
            return chosen;
        }
        // Nothing matched, or a staff layout is off: keep a normal sidebar rather than nothing.
        for (ScoreboardProfile candidate : profiles) {
            if (candidate.isEnabled() && !isStaffLayout(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    private void apply(Player player, PlayerSession session, ScoreboardProfile profile) {
        try {
            draw(player, session, profile);
        } catch (RuntimeException failure) {
            if (reportedFailure.compareAndSet(false, true)) {
                org.bukkit.Bukkit.getLogger().warning("[Honeycomb] The scoreboard could not be drawn: "
                        + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                player.sendMessage(Text.parse("<red>Honeycomb: your scoreboard could not be drawn ("
                        + failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()) + "). <gray>Send this line to the developer."));
            }
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean reportedFailure =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean plainRows =
            new java.util.concurrent.atomic.AtomicBoolean();

    private void draw(Player player, PlayerSession session, ScoreboardProfile profile) {
        if (session.sidebar.hideNumbers != profile.hideNumbers() && session.sidebar.board != null) {
            session.sidebar.board.disposeSidebar();
        }
        session.sidebar.hideNumbers = profile.hideNumbers();
        session.sidebar.profile = profile.name();

        PlayerBoard board = BoardAccess.acquire(player, session);
        if (board == null) {
            return;
        }
        Sidebar sidebar = board.sidebar(profile.hideNumbers());

        PlaceholderContext context = PlaceholderContext.of(player);
        Component title = pipeline.render(profile.title(), context);
        List<Component> lines = pipeline.renderBlock(profile.lines(), context);
        try {
            sidebar.update(title, lines);
        } catch (RuntimeException failure) {
            // A row the server refuses must not cost the player the whole sidebar.
            if (plainRows.compareAndSet(false, true)) {
                org.bukkit.Bukkit.getLogger().warning("[Honeycomb] Scoreboard fell back to plain rows: "
                        + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            List<Component> plain = new ArrayList<>(profile.lines().size());
            for (String raw : profile.lines()) {
                plain.add(Component.text(Text.stripTags(raw)));
            }
            sidebar.update(Component.text(Text.stripTags(profile.title())), plain);
        }
    }

    private void clear(Player player, PlayerSession session) {
        session.sidebar.profile = null;
        PlayerBoard board = session.sidebar.board;
        if (board != null) {
            board.disposeSidebar();
        }
        BoardAccess.releaseIfUnused(player, session);
    }

    @Override
    public void forget(Player player) {
        PlayerSession session = sessions.peek(player);
        if (session == null) {
            return;
        }
        // The client is gone: drop the references, the server frees the board.
        PlayerBoard board = session.sidebar.board;
        if (board != null) {
            board.dispose();
            session.sidebar.board = null;
        }
        session.sidebar.reset();
    }

    @Override
    public String describe() {
        return profiles.size() + " profile(s), interval " + serviceInterval + "t";
    }
}
