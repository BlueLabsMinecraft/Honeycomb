package dev.bluelabs.honeycomb.sorting;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.player.PlayerSession;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts the tablist by group, then by name.
 *
 * <p>Ordering uses Paper's player list order, which is a real client side sort
 * value and does not need a team per group. Players keep their team based nametag
 * decoration; the two systems do not interfere.</p>
 */
public final class SortingService implements DisplaySystem {

    private static final String STAFF_PERMISSION = "honeycomb.staff";

    private final ConfigManager configManager;
    private final PlayerSessionRegistry sessions;
    private final GroupProvider groups;
    private final TickClock clock;
    private final ValidationReport report;

    private SortingRule rule = SortingRule.empty();
    private boolean enabled;
    private int serviceInterval = 100;
    private long lastAppliedTick = Long.MIN_VALUE;

    /**
     * @param configManager configuration manager
     * @param sessions      player sessions
     * @param groups        permission group source
     * @param clock         shared tick clock
     * @param report        validation report
     */
    public SortingService(ConfigManager configManager,
                          PlayerSessionRegistry sessions,
                          GroupProvider groups,
                          TickClock clock,
                          ValidationReport report) {
        this.configManager = configManager;
        this.sessions = sessions;
        this.groups = groups;
        this.clock = clock;
        this.report = report;
    }

    @Override
    public String name() {
        return "sorting";
    }

    @Override
    public void enable() {
        ConfigurationSection root = configManager.yaml(ConfigType.SORTING).getConfigurationSection("sorting");
        this.enabled = root != null && root.getBoolean("enabled", true);
        if (!enabled) {
            this.rule = SortingRule.empty();
            return;
        }
        List<String> order = new ArrayList<>();
        for (String entry : ConfigValues.strings(root, "groups")) {
            order.add(entry);
        }
        Map<String, Integer> weights = new HashMap<>();
        ConfigurationSection weightSection = root.getConfigurationSection("weights");
        if (weightSection != null) {
            for (String key : weightSection.getKeys(false)) {
                weights.put(key.toLowerCase(java.util.Locale.ROOT), weightSection.getInt(key));
            }
        }
        this.rule = SortingRule.of(order, weights,
                root.getBoolean("alphabetical-within-group", true),
                root.getBoolean("staff-first", false));
        this.serviceInterval = Math.max(1, root.getInt("update-interval", 100));
        this.lastAppliedTick = Long.MIN_VALUE;
        if (order.isEmpty() && weights.isEmpty()) {
            report.warning("sorting.yml", "No groups configured; players are sorted alphabetically only");
        }
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListOrder(0);
            PlayerSession session = sessions.peek(player);
            if (session != null) {
                session.sortOrder = Integer.MIN_VALUE;
            }
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
        long now = clock.tick();
        if (now == lastAppliedTick || !clock.isDue(serviceInterval, 0)) {
            return;
        }
        lastAppliedTick = now;
        applyAll();
    }

    @Override
    public void refresh(Player player) {
        if (enabled) {
            applyAll();
        }
    }

    private void applyAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<SortKey> keys = new ArrayList<>(online.size());
        Map<java.util.UUID, SortKey> byId = new HashMap<>(online.size());
        for (Player player : online) {
            SortKey key = new SortKey(player.getUniqueId(), player.getName(),
                    rule.rankFor(groups.group(player)), player.hasPermission(STAFF_PERMISSION));
            keys.add(key);
            byId.put(key.uniqueId(), key);
        }
        Comparator<SortKey> comparator = (left, right) ->
                left.compare(right, rule.alphabeticalWithinGroup(), rule.staffFirst());
        keys.sort(comparator);

        int total = keys.size();
        for (int index = 0; index < total; index++) {
            SortKey key = keys.get(index);
            Player player = Bukkit.getPlayer(key.uniqueId());
            if (player == null) {
                continue;
            }
            // Higher order values are shown higher in the list.
            int order = (total - index) * 10;
            PlayerSession session = sessions.get(player);
            if (session.sortOrder == order) {
                continue;
            }
            player.setPlayerListOrder(order);
            session.sortOrder = order;
        }
    }

    @Override
    public void forget(Player player) {
        // Nothing to release: the order value disappears with the player's entry.
    }

    @Override
    public String describe() {
        return rule.groupOrder().size() + " group(s), interval " + serviceInterval + "t";
    }

    /**
     * @return the active rule, exposed for diagnostics
     */
    public SortingRule rule() {
        return rule;
    }
}
