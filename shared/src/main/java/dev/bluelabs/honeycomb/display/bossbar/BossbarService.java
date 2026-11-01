package dev.bluelabs.honeycomb.display.bossbar;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.Settings;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.display.WorldProfiles;
import dev.bluelabs.honeycomb.display.MatchContext;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import dev.bluelabs.honeycomb.util.Numbers;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages Honeycomb's bossbars.
 *
 * <p>Only bars this service created are ever shown or hidden, so other plugins'
 * bossbars keep working untouched. Several definitions can be active at once; they
 * are shown highest priority first.</p>
 */
public final class BossbarService implements DisplaySystem {

    private final ConfigManager configManager;
    private final Settings settings;
    private final RenderPipeline pipeline;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final TickClock clock;
    private final ValidationReport report;

    private List<BossbarDefinition> definitions = new ArrayList<>();
    private boolean enabled;
    private int serviceInterval = 20;
    private WorldProfiles worldProfiles;

    /**
     * @param configManager configuration manager
     * @param settings      general settings
     * @param pipeline      render pipeline
     * @param sessions      player sessions
     * @param groups        permission group source
     * @param clock         shared tick clock
     * @param report        validation report
     */
    public BossbarService(ConfigManager configManager,
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
        return "bossbars";
    }

    @Override
    public void enable() {
        ConfigurationSection root = configManager.yaml(ConfigType.BOSSBARS).getConfigurationSection("bossbars");
        this.enabled = root != null && root.getBoolean("enabled", true);
        if (!enabled) {
            this.definitions = new ArrayList<>();
            return;
        }
        this.definitions = BossbarDefinition.loadAll(root, report);
        this.definitions.sort(Comparator.comparingInt(BossbarDefinition::priority).reversed());
        int interval = root.getInt("update-interval", settings.defaultUpdateInterval());
        this.serviceInterval = Math.max(1, interval);
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideAll(player);
        }
        this.enabled = false;
    }

    @Override
    public void reload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideAll(player);
        }
        enable();
    }

    private void hideAll(Player player) {
        PlayerSession session = sessions.peek(player);
        if (session == null) {
            return;
        }
        for (BossBar bar : session.bossbars.shown.values()) {
            player.hideBossBar(bar);
        }
        session.bossbars.reset();
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
        if (settings.isWorldDisabled(player.getWorld().getName())) {
            hideAll(player);
            return;
        }
        if (worldProfiles != null && worldProfiles.isDisabled("bossbars", player.getWorld().getName())) {
            hideAll(player);
            return;
        }
        if (settings.respectBypassPermission() && player.hasPermission("honeycomb.bypass")) {
            return;
        }
        apply(player, session);
    }

    @Override
    public void refresh(Player player) {
        if (!enabled) {
            return;
        }
        apply(player, sessions.get(player));
    }

    private void apply(Player player, PlayerSession session) {
        long now = clock.tick();
        removeExpired(player, session, now);

        MatchContext context = MatchContext.of(player, groups.group(player))
                .withPlaceholders(text -> pipeline.placeholders().expand(text, PlaceholderContext.of(player)));
        Set<String> applicable = new HashSet<>();
        PlaceholderContext placeholders = PlaceholderContext.of(player);

        for (BossbarDefinition definition : definitions) {
            if (!definition.appliesTo(context)) {
                continue;
            }
            applicable.add(definition.name());
            Component title = pipeline.render(definition.titleTemplate(), placeholders);
            float progress = resolveProgress(definition, placeholders, session, now);

            BossBar bar = session.bossbars.shown.get(definition.name());
            if (bar == null) {
                bar = BossBar.bossBar(title, progress, definition.color(), definition.overlay());
                player.showBossBar(bar);
                session.bossbars.shown.put(definition.name(), bar);
                if (definition.durationSeconds() > 0) {
                    session.bossbars.expiry.put(definition.name(), now + definition.durationSeconds() * 20L);
                }
                continue;
            }
            if (!bar.name().equals(title)) {
                bar.name(title);
            }
            if (Math.abs(bar.progress() - progress) > 0.001F) {
                bar.progress(progress);
            }
        }

        Iterator<Map.Entry<String, BossBar>> iterator = session.bossbars.shown.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BossBar> entry = iterator.next();
            if (!applicable.contains(entry.getKey())) {
                player.hideBossBar(entry.getValue());
                session.bossbars.expiry.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void removeExpired(Player player, PlayerSession session, long now) {
        Iterator<Map.Entry<String, Long>> iterator = session.bossbars.expiry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue;
            }
            BossBar bar = session.bossbars.shown.remove(entry.getKey());
            if (bar != null) {
                player.hideBossBar(bar);
            }
            iterator.remove();
        }
    }

    private float resolveProgress(BossbarDefinition definition, PlaceholderContext context,
                                  PlayerSession session, long now) {
        if (definition.durationSeconds() > 0 && definition.countdown()) {
            Long expiry = session.bossbars.expiry.get(definition.name());
            if (expiry != null) {
                long remaining = Math.max(0L, expiry - now);
                float fraction = (float) remaining / (definition.durationSeconds() * 20.0F);
                return (float) Numbers.clamp(fraction, 0.0D, 1.0D);
            }
        }
        String raw = definition.progressSetting();
        if (raw == null || raw.isBlank()) {
            return 1.0F;
        }
        String expanded = pipeline.prepare(raw, context);
        try {
            double value = Double.parseDouble(expanded.trim());
            // Both 0-1 fractions and 0-100 percentages are accepted.
            if (value > 1.0D) {
                value /= 100.0D;
            }
            return (float) Numbers.clamp(value, 0.0D, 1.0D);
        } catch (NumberFormatException exception) {
            return 1.0F;
        }
    }

    @Override
    public void forget(Player player) {
        PlayerSession session = sessions.peek(player);
        if (session != null) {
            session.bossbars.reset();
        }
    }

    @Override
    public String describe() {
        return definitions.size() + " definition(s), interval " + serviceInterval + "t";
    }
}
