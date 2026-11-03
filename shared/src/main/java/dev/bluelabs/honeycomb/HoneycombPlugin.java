package dev.bluelabs.honeycomb;

import org.bukkit.Bukkit;

import dev.bluelabs.honeycomb.animation.AnimationRegistry;
import dev.bluelabs.honeycomb.api.HoneycombApiImpl;
import dev.bluelabs.honeycomb.api.HoneycombApiProvider;
import dev.bluelabs.honeycomb.api.event.HoneycombReloadEvent;
import dev.bluelabs.honeycomb.command.CommandRegistry;
import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.Messages;
import dev.bluelabs.honeycomb.config.PanelOverrides;
import dev.bluelabs.honeycomb.config.Settings;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.diagnostics.DiagnosticsService;
import dev.bluelabs.honeycomb.display.DisplayCoordinator;
import dev.bluelabs.honeycomb.panel.ConfigPanel;
import dev.bluelabs.honeycomb.updates.UpdateLogCommand;
import dev.bluelabs.honeycomb.updates.UpdateLogService;
import dev.bluelabs.honeycomb.server.BrandService;
import dev.bluelabs.honeycomb.server.CommandHider;
import dev.bluelabs.honeycomb.server.ServerListService;
import dev.bluelabs.honeycomb.display.WorldProfiles;
import dev.bluelabs.honeycomb.display.belowname.BelowNameService;
import dev.bluelabs.honeycomb.display.actionbar.ActionbarService;
import dev.bluelabs.honeycomb.display.hologram.NameHologramService;
import dev.bluelabs.honeycomb.display.bossbar.BossbarService;
import dev.bluelabs.honeycomb.display.nametag.NametagService;
import dev.bluelabs.honeycomb.display.scoreboard.ScoreboardService;
import dev.bluelabs.honeycomb.display.tablist.TablistService;
import dev.bluelabs.honeycomb.integration.IntegrationManager;
import dev.bluelabs.honeycomb.listener.PlayerConnectionListener;
import dev.bluelabs.honeycomb.network.NetworkService;
import dev.bluelabs.honeycomb.placeholder.InternalPlaceholders;
import dev.bluelabs.honeycomb.placeholder.PlaceholderRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderService;
import dev.bluelabs.honeycomb.player.PlayerSessionRegistry;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.sorting.SortingService;
import dev.bluelabs.honeycomb.text.RenderPipeline;
import dev.bluelabs.honeycomb.util.MinecraftVersions;
import dev.bluelabs.honeycomb.util.TimeFormats;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Honeycomb entry point.
 *
 * <p>Startup order matters: configuration first, then the shared engines, then the
 * integrations that feed those engines, then the displays. Everything the plugin
 * creates is owned by a service that can be stopped and started again, which is
 * what makes {@code /honeycomb reload} safe.</p>
 */
public final class HoneycombPlugin extends JavaPlugin {

    /**
     * Outcome of a reload.
     *
     * @param millis how long the reload took
     * @param report configuration warnings and errors
     */
    public record ReloadResult(long millis, ValidationReport report) {
    }

    private static HoneycombPlugin instance;

    private final TickClock clock = new TickClock();
    private final long startMillis = System.currentTimeMillis();

    private ConfigManager configManager;
    private ValidationReport report;
    private Settings settings;
    private Messages messages;

    private TaskRegistry tasks;
    private PlaceholderRegistry placeholderRegistry;
    private PlaceholderService placeholderService;
    private InternalPlaceholders internalPlaceholders;
    private AnimationRegistry animations;
    private dev.bluelabs.honeycomb.preset.PresetService presets;
    private RenderPipeline pipeline;

    private PlayerSessionRegistry sessions;
    private WorldProfiles worldProfiles;
    private IntegrationManager integrations;
    private NetworkService network;

    private TablistService tablist;
    private ScoreboardService scoreboard;
    private NametagService nametags;
    private BossbarService bossbars;
    private BelowNameService belowName;
    private NameHologramService holograms;
    private ActionbarService actionbar;
    private SortingService sorting;
    private DisplayCoordinator coordinator;
    private DiagnosticsService diagnostics;
    private CommandRegistry commandRegistry;
    private PanelOverrides panelOverrides;
    private UpdateLogService updates;
    private ConfigPanel panel;
    private BrandService brand;
    private ServerListService serverList;
    private CommandHider commandHider;

    /**
     * @return the running plugin
     */
    public static HoneycombPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        panelOverrides = new PanelOverrides(this);
        panelOverrides.load();
        configManager.useOverrides(panelOverrides);
        report = configManager.loadAll();
        settings = new Settings(configManager, report);
        messages = new Messages(configManager, report);
        tasks = new TaskRegistry(this);

        placeholderRegistry = new PlaceholderRegistry();
        placeholderService = new PlaceholderService(placeholderRegistry, clock);
        animations = new AnimationRegistry(configManager, report);
        animations.loadConfigured();

        integrations = new IntegrationManager(this, configManager, report);
        integrations.detect();
        network = new NetworkService(this, configManager, report, tasks);
        createPlaceholderEngine();

        pipeline = new RenderPipeline(placeholderService, animations, clock);
        sessions = new PlayerSessionRegistry(clock);

        tablist = new TablistService(this, configManager, settings, pipeline, sessions,
                integrations.groups(), clock, report);
        scoreboard = new ScoreboardService(configManager, settings, pipeline, sessions,
                integrations.groups(), clock, report);
        nametags = new NametagService(configManager, settings, pipeline, sessions,
                integrations.groups(), integrations.states(), clock, report);
        bossbars = new BossbarService(configManager, settings, pipeline, sessions,
                integrations.groups(), clock, report);
        belowName = new BelowNameService(configManager, settings, pipeline, sessions,
                integrations.groups(), clock, report);
        holograms = new NameHologramService(configManager, pipeline);
        actionbar = new ActionbarService(configManager, pipeline);
        sorting = new SortingService(configManager, sessions, integrations.groups(), clock, report);

        worldProfiles = new WorldProfiles(configManager, report);
        worldProfiles.load();
        tablist.setWorldProfiles(worldProfiles);
        scoreboard.setWorldProfiles(worldProfiles);
        nametags.setWorldProfiles(worldProfiles);
        bossbars.setWorldProfiles(worldProfiles);
        belowName.setWorldProfiles(worldProfiles);

        coordinator = new DisplayCoordinator(tasks, clock, placeholderService);
        coordinator.register(tablist);
        coordinator.register(scoreboard);
        coordinator.register(nametags);
        coordinator.register(bossbars);
        coordinator.register(belowName);
        coordinator.register(holograms);
        coordinator.register(actionbar);
        coordinator.register(sorting);

        diagnostics = new DiagnosticsService(tasks, sessions, placeholderService, coordinator, integrations, network);
        coordinator.setMetrics(diagnostics);

        commandRegistry = new CommandRegistry(this);
        commandRegistry.register();
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(sessions, coordinator, tasks, network), this);

        network.enable();
        coordinator.start();
        tasks.repeating("placeholder-cache", 200L, settings.cacheSweepInterval(), placeholderService::sweepCache);
        try {
            panel = new ConfigPanel(this);
        } catch (Throwable missingDialogApi) {
            // Paper before 1.21.9 has no dialog API. Everything else keeps working.
            panel = null;
            getLogger().info("Dialog panel unavailable on this server version (needs Paper 1.21.9 or newer)");
        }
        updates = new UpdateLogService(this);
        if (getCommand("updatelog") != null) {
            getCommand("updatelog").setExecutor(new UpdateLogCommand(this));
        }

        serverList = new ServerListService(this);
        Bukkit.getPluginManager().registerEvents(serverList, this);
        brand = new BrandService(this);
        commandHider = new CommandHider(this);
        commandHider.reload();
        Bukkit.getPluginManager().registerEvents(commandHider, this);
        Bukkit.getPluginManager().registerEvents(brand, this);
        applyBrand();
        HoneycombApiProvider.register(new HoneycombApiImpl(this));

        logStartup();
    }

    private void createPlaceholderEngine() {
        internalPlaceholders = new InternalPlaceholders(placeholderRegistry, configManager,
                integrations.groups(), integrations.states(), integrations.economy(), network,
                startMillis, report);
        int ttl = configManager.yaml(dev.bluelabs.honeycomb.config.ConfigType.PLACEHOLDERS)
                .getInt("cache-ttl-ticks", 10);
        placeholderService.configure(ttl, settings.warnUnknownPlaceholders());
        placeholderService.setExternalResolver(integrations.placeholderBridge());
    }

    private void logStartup() {
        getLogger().info("Honeycomb " + BuildInfo.version() + " for Minecraft "
                + BuildInfo.minecraftRange() + " (Java " + BuildInfo.javaTarget() + ")");
        String serverVersion = MinecraftVersions.extractMinecraftVersion(getServer().getBukkitVersion());
        getLogger().info("Server reports Minecraft " + serverVersion);
        if (!MinecraftVersions.isSupported(serverVersion)) {
            getLogger().warning("Minecraft " + serverVersion + " is outside the verified range "
                    + String.join(", ", MinecraftVersions.testedRanges())
                    + ". Honeycomb will run, but this combination is untested.");
        }
        for (ValidationReport.Issue issue : report.issues()) {
            switch (issue.severity()) {
                case ERROR -> getLogger().severe(issue.format());
                case WARNING -> getLogger().warning(issue.format());
                default -> {
                    if (settings.debug()) {
                        getLogger().info(issue.format());
                    }
                }
            }
        }
        getLogger().info("Configuration: " + report.summary());
        for (String note : diagnostics.conflictNotes()) {
            if (settings.detectConflicts()) {
                getLogger().warning("Conflict: " + note);
            }
        }
    }

    @Override
    public void onDisable() {
        HoneycombApiProvider.unregister();
        if (coordinator != null) {
            coordinator.stop();
        }
        if (network != null) {
            network.disable();
        }
        tasks.cancelAll();
        sessions.clear();
        getLogger().info("Honeycomb disabled after "
                + TimeFormats.clock(System.currentTimeMillis() - startMillis));
        instance = null;
    }

    /**
     * Reloads every configuration file and re-applies all displays.
     *
     * @return the reload duration and validation report
     */
    /** Re-reads the hidden command list and re-applies the F3 brand. */
    public void applyServerSettings() {
        commandHider.reload();
        applyBrand();
    }

    private void applyBrand() {
        dev.bluelabs.honeycomb.config.ConfigType type = dev.bluelabs.honeycomb.config.ConfigType.CONFIG;
        brand.apply(configManager.yaml(type).getBoolean("brand.enabled", false),
                configManager.yaml(type).getString("brand.text", ""));
    }

    public ReloadResult reload() {
        long started = System.nanoTime();
        try {
            return reloadUnchecked(started);
        } catch (RuntimeException | LinkageError failure) {
            // A display that refuses to reload must not break the panel that saved the
            // change: log it, keep the old state, and let the caller carry on.
            getLogger().severe("[Honeycomb] Reload failed: " + failure.getClass().getName()
                    + ": " + failure.getMessage());
            return new ReloadResult((System.nanoTime() - started) / 1_000_000L, new ValidationReport());
        }
    }

    private ReloadResult reloadUnchecked(long started) {
        panelOverrides.load();
        ValidationReport reloadReport = configManager.reloadAll();
        worldProfiles.load();
        animations.loadConfigured();
        integrations.detect();
        createPlaceholderEngine();
        network.enable();
        coordinator.reloadAll();
        applyServerSettings();
        serverList.reload();
        tasks.repeating("placeholder-cache", 200L, settings.cacheSweepInterval(), placeholderService::sweepCache);

        long millis = (System.nanoTime() - started) / 1_000_000L;
        for (ValidationReport.Issue issue : reloadReport.issues()) {
            if (issue.severity() == ValidationReport.Severity.ERROR) {
                getLogger().severe(issue.format());
            } else if (issue.severity() == ValidationReport.Severity.WARNING) {
                getLogger().warning(issue.format());
            }
        }
        getServer().getPluginManager().callEvent(new HoneycombReloadEvent(
                millis,
                reloadReport.issues(ValidationReport.Severity.WARNING).size(),
                reloadReport.issues(ValidationReport.Severity.ERROR).size()));
        return new ReloadResult(millis, reloadReport);
    }

    /**
     * @return the plugin version
     */
    public String version() {
        return BuildInfo.version();
    }

    /**
     * @return the Minecraft range this artifact supports
     */
    public String minecraftRange() {
        return BuildInfo.minecraftRange();
    }

    /**
     * @return the maintenance and MOTD service
     */
    public ServerListService serverList() {
        return serverList;
    }

    /**
     * @return configuration manager
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * @return the report from the most recent load
     */
    public ValidationReport report() {
        return report;
    }

    /**
     * @return general settings
     */
    public Settings settings() {
        return settings;
    }

    /**
     * @return message lookup
     */
    /**
     * @return values written by the in-game panel
     */
    public PanelOverrides panelOverrides() {
        return panelOverrides;
    }

    /**
     * @return the dialog based configuration panel
     */
    /**
     * @return the update log published by admins
     */
    public UpdateLogService updates() {
        return updates;
    }

    public ConfigPanel panel() {
        return panel;
    }

    public Messages messages() {
        return messages;
    }

    /**
     * @return task registry
     */
    public TaskRegistry tasks() {
        return tasks;
    }

    /**
     * @return the placeholder registry
     */
    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    /**
     * @return the placeholder service
     */
    public PlaceholderService placeholders() {
        return placeholderService;
    }

    /**
     * @return the internal placeholder set
     */
    public InternalPlaceholders internalPlaceholders() {
        return internalPlaceholders;
    }

    /**
     * @return animation registry
     */
    public AnimationRegistry animations() {
        return animations;
    }

    /**
     * @return the render pipeline used by every display
     */
    public dev.bluelabs.honeycomb.preset.PresetService presets() {
        if (presets == null) {
            presets = new dev.bluelabs.honeycomb.preset.PresetService(this);
            presets.ensureDefaults();
        }
        return presets;
    }

    public RenderPipeline pipeline() {
        return pipeline;
    }

    /**
     * @return player session registry
     */
    public PlayerSessionRegistry sessions() {
        return sessions;
    }

    /**
     * @return integration manager
     */
    public IntegrationManager integrations() {
        return integrations;
    }

    /**
     * @return network service
     */
    public NetworkService network() {
        return network;
    }

    /**
     * @return per-world overrides
     */
    public WorldProfiles worldProfiles() {
        return worldProfiles;
    }

    /**
     * @return the tablist system
     */
    public TablistService tablist() {
        return tablist;
    }

    /**
     * @return the scoreboard system
     */
    public ScoreboardService scoreboard() {
        return scoreboard;
    }

    /**
     * @return the nametag system
     */
    public NametagService nametags() {
        return nametags;
    }

    /**
     * @return the bossbar system
     */
    public BossbarService bossbars() {
        return bossbars;
    }

    /**
     * @return the below-name system
     */
    public BelowNameService belowName() {
        return belowName;
    }

    /**
     * @return the sorting system
     */
    public SortingService sorting() {
        return sorting;
    }

    /**
     * @return the display coordinator
     */
    public DisplayCoordinator coordinator() {
        return coordinator;
    }

    /**
     * @return diagnostics collector
     */
    public DiagnosticsService diagnostics() {
        return diagnostics;
    }

    /**
     * @return command registry
     */
    public CommandRegistry commandRegistry() {
        return commandRegistry;
    }

    /**
     * @return the shared tick clock
     */
    public TickClock clock() {
        return clock;
    }

    /**
     * @return the moment the plugin enabled
     */
    public long startMillis() {
        return startMillis;
    }
}
