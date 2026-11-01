package dev.bluelabs.honeycomb.display.belowname;

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
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the value under a player's nametag.
 *
 * <p>The rendered value only depends on the player it describes, never on who is
 * looking, so it is computed once per player and reused for every viewer. That
 * keeps a hundred player server at a hundred renders per cycle instead of ten
 * thousand.</p>
 */
public final class BelowNameService implements DisplaySystem {

    private final ConfigManager configManager;
    private final Settings settings;
    private final RenderPipeline pipeline;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final TickClock clock;
    private final ValidationReport report;

    private final Map<UUID, Component> values = new HashMap<>();
    private final Map<UUID, Long> computedAt = new HashMap<>();

    private List<BelowNameProfile> profiles = new ArrayList<>();
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
    public BelowNameService(ConfigManager configManager,
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
        return "belowname";
    }

    @Override
    public void enable() {
        ConfigurationSection root = configManager.yaml(ConfigType.BELOWNAME).getConfigurationSection("belowname");
        this.enabled = root != null && root.getBoolean("enabled", false);
        this.values.clear();
        this.computedAt.clear();
        if (!enabled) {
            this.profiles = new ArrayList<>();
            return;
        }
        int defaultInterval = root.getInt("update-interval", settings.defaultUpdateInterval());
        this.profiles = BelowNameProfile.loadAll(root, defaultInterval, report);
        int minimum = Integer.MAX_VALUE;
        for (BelowNameProfile profile : profiles) {
            minimum = Math.min(minimum, profile.updateInterval());
        }
        this.serviceInterval = minimum == Integer.MAX_VALUE ? defaultInterval : minimum;
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.peek(player);
            if (session != null) {
                clear(player, session);
            }
        }
        this.values.clear();
        this.computedAt.clear();
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
    public void tick(Player viewer) {
        if (!enabled || profiles.isEmpty()) {
            return;
        }
        PlayerSession session = sessions.get(viewer);
        if (!clock.isDue(serviceInterval, session.updateOffset())) {
            return;
        }
        if (settings.isWorldDisabled(viewer.getWorld().getName())) {
            return;
        }
        if (settings.respectBypassPermission() && viewer.hasPermission("honeycomb.bypass")) {
            return;
        }
        BelowNameProfile profile = selectProfile(viewer);
        if (profile == null) {
            if (session.belowName.profile != null) {
                clear(viewer, session);
            }
            return;
        }
        apply(viewer, session, profile);
    }

    @Override
    public void refresh(Player viewer) {
        if (!enabled) {
            return;
        }
        PlayerSession session = sessions.get(viewer);
        BelowNameProfile profile = selectProfile(viewer);
        if (profile == null) {
            clear(viewer, session);
            return;
        }
        apply(viewer, session, profile);
    }

    private BelowNameProfile selectProfile(Player viewer) {
        String worldName = viewer.getWorld().getName();
        if (worldProfiles != null) {
            if (worldProfiles.isDisabled("belowname", worldName)) {
                return null;
            }
            String forced = worldProfiles.profileName("belowname", worldName);
            if (forced != null) {
                for (BelowNameProfile candidate : profiles) {
                    if (candidate.name().equalsIgnoreCase(forced) && candidate.isEnabled()) {
                        return candidate;
                    }
                }
            }
        }
        return ProfileSelector.select(profiles, MatchContext.of(viewer, groups.group(viewer))
                .withPlaceholders(text -> pipeline.placeholders().expand(text, PlaceholderContext.of(viewer))));
    }

    private void apply(Player viewer, PlayerSession session, BelowNameProfile profile) {
        boolean healthBased = profile.mode() == BelowNameProfile.Mode.HEALTH;
        if (session.belowName.healthMode != healthBased && session.sidebar.board != null) {
            session.sidebar.board.disposeBelowName();
            session.belowName.lastValues.clear();
        }
        session.belowName.healthMode = healthBased;
        session.belowName.profile = profile.name();

        PlayerBoard board = BoardAccess.acquire(viewer, session);
        Objective objective = board.belowNameObjective(healthBased);
        if (healthBased) {
            // Minecraft's own health criteria keeps the value correct without any writes.
            return;
        }

        refreshValues(profile);
        for (Player target : Bukkit.getOnlinePlayers()) {
            Component rendered = values.get(target.getUniqueId());
            if (rendered == null) {
                continue;
            }
            if (rendered.equals(session.belowName.lastValues.get(target.getUniqueId()))) {
                continue;
            }
            Score score = objective.getScore(target.getName());
            score.setScore(0);
            score.numberFormat(NumberFormat.fixed(rendered));
            session.belowName.lastValues.put(target.getUniqueId(), rendered);
        }
    }

    private void refreshValues(BelowNameProfile profile) {
        long now = clock.tick();
        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID id = target.getUniqueId();
            Long last = computedAt.get(id);
            if (last != null && now - last < profile.updateInterval()) {
                continue;
            }
            values.put(id, pipeline.render(profile.template(), PlaceholderContext.of(target)));
            computedAt.put(id, now);
        }
    }

    private void clear(Player viewer, PlayerSession session) {
        session.belowName.profile = null;
        PlayerBoard board = session.sidebar.board;
        if (board != null) {
            board.disposeBelowName();
        }
        session.belowName.lastValues.clear();
        BoardAccess.releaseIfUnused(viewer, session);
    }

    @Override
    public void forget(Player player) {
        PlayerSession session = sessions.peek(player);
        if (session != null) {
            session.belowName.lastValues.clear();
            session.belowName.reset();
        }
        values.remove(player.getUniqueId());
        computedAt.remove(player.getUniqueId());
        for (PlayerSession other : sessions.all()) {
            other.belowName.lastValues.remove(player.getUniqueId());
        }
    }

    @Override
    public String describe() {
        return profiles.size() + " profile(s), interval " + serviceInterval + "t";
    }
}
