package dev.bluelabs.honeycomb.diagnostics;

import dev.bluelabs.honeycomb.display.DisplayCoordinator;
import dev.bluelabs.honeycomb.display.DisplaySystem;
import dev.bluelabs.honeycomb.integration.IntegrationManager;
import dev.bluelabs.honeycomb.network.NetworkService;
import dev.bluelabs.honeycomb.placeholder.PlaceholderService;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import dev.bluelabs.honeycomb.util.MinecraftVersions;
import dev.bluelabs.honeycomb.util.TimeFormats;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects real measurements and produces the debug report.
 *
 * <p>Every number here is measured or read from the server. Nothing is estimated:
 * when a value is unknown it is reported as unknown.</p>
 */
public final class DiagnosticsService implements TickMetrics {

    private final TaskRegistry tasks;
    private final PlayerSessionRegistry sessions;
    private final PlaceholderService placeholders;
    private final DisplayCoordinator coordinator;
    private final IntegrationManager integrations;
    private final NetworkService network;
    private final long startMillis = System.currentTimeMillis();

    private final LongAdder passes = new LongAdder();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();
    private final LongAdder errors = new LongAdder();

    /**
     * @param tasks        task registry
     * @param sessions     player sessions
     * @param placeholders placeholder service
     * @param coordinator  display coordinator
     * @param integrations integration manager
     * @param network      network service
     */
    public DiagnosticsService(TaskRegistry tasks,
                              PlayerSessionRegistry sessions,
                              PlaceholderService placeholders,
                              DisplayCoordinator coordinator,
                              IntegrationManager integrations,
                              NetworkService network) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.placeholders = placeholders;
        this.coordinator = coordinator;
        this.integrations = integrations;
        this.network = network;
    }

    @Override
    public void recordPass(long nanos) {
        passes.increment();
        totalNanos.addAndGet(nanos);
        maxNanos.accumulateAndGet(nanos, Math::max);
    }

    @Override
    public void recordError() {
        errors.increment();
    }

    /**
     * @return average display pass duration in milliseconds
     */
    public double averagePassMillis() {
        long count = passes.sum();
        return count == 0L ? 0.0D : (double) totalNanos.get() / (double) count / 1_000_000.0D;
    }

    /**
     * @return slowest observed pass in milliseconds
     */
    public double worstPassMillis() {
        return (double) maxNanos.get() / 1_000_000.0D;
    }

    /**
     * @return number of display passes measured
     */
    public long passCount() {
        return passes.sum();
    }

    /**
     * @return number of display errors caught
     */
    public long errorCount() {
        return errors.sum();
    }

    /**
     * Finds plugins that manage the same display elements Honeycomb manages.
     *
     * @return one advice line per detected plugin, empty when none were found
     */
    public List<String> conflictNotes() {
        List<String> notes = new ArrayList<>();
        for (String name : List.of("TAB", "scoreboard-revision", "FeatherBoard", "NametagEdit", "DeluxeMenus")) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                notes.add(name + " detected: disable the same element (tablist, sidebar, nametags,"
                        + " bossbars) in one of the two plugins");
            }
        }
        return notes;
    }

    /**
     * Builds the full debug report.
     *
     * @param plugin    plugin instance
     * @param version   plugin version
     * @return the report as lines
     */
    public List<String> buildReport(Plugin plugin, String version) {
        List<String> lines = new ArrayList<>();
        String minecraftVersion = MinecraftVersions.extractMinecraftVersion(Bukkit.getBukkitVersion());
        lines.add("Honeycomb " + version + " | server " + minecraftVersion + " | java "
                + System.getProperty("java.version"));
        lines.add("Uptime " + TimeFormats.clock(System.currentTimeMillis() - startMillis)
                + " | online " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        lines.add("TPS " + format(Bukkit.getTPS()) + " | MSPT " + format(Bukkit.getAverageTickTime()));
        lines.add("Display passes " + passes.sum() + " | avg " + String.format(java.util.Locale.ROOT, "%.3f", averagePassMillis())
                + "ms | worst " + String.format(java.util.Locale.ROOT, "%.3f", worstPassMillis())
                + "ms | errors " + errors.sum());
        lines.add("Tasks " + tasks.activeTaskCount() + " " + tasks.repeatingTaskNames());
        lines.add("Sessions " + sessions.size() + " | placeholder cache " + placeholders.cacheSize());
        if (!placeholders.unknownPlaceholders().isEmpty()) {
            lines.add("Unknown placeholders " + placeholders.unknownPlaceholders());
        }
        for (DisplaySystem system : coordinator.systems()) {
            lines.add(" - " + system.name() + ": " + (system.isEnabled() ? "enabled" : "disabled")
                    + " (" + system.describe() + ")");
        }
        for (IntegrationManager.Status status : integrations.statuses()) {
            lines.add(" integration " + status.name() + ": "
                    + (status.available() ? "yes" : "no") + " (" + status.detail() + ")");
        }
        lines.add("Network: " + network.sourceName()
                + (network.available() ? " online " + network.onlineEverywhere() : " no proxy answer yet"));
        lines.addAll(conflictNotes());
        return lines;
    }

    private String format(double[] values) {
        if (values == null || values.length == 0) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, values.length);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append('/');
            }
            builder.append(String.format(java.util.Locale.ROOT, "%.2f", values[index]));
        }
        return builder.toString();
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
