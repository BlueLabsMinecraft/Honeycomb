package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed access to {@code config.yml}.
 *
 * <p>Values are read from the live YAML each call so {@code /honeycomb reload}
 * takes effect immediately without a second cache to keep in sync. Only update
 * loops that run every tick cache these values, and they re-read on reload.</p>
 */
public final class Settings {

    private final ConfigManager configManager;
    private final ValidationReport report;

    /**
     * @param configManager configuration manager
     * @param report        report that receives validation warnings
     */
    public Settings(ConfigManager configManager, ValidationReport report) {
        this.configManager = configManager;
        this.report = report;
    }

    private YamlConfiguration yaml() {
        return configManager.yaml(ConfigType.CONFIG);
    }

    /**
     * @return true when verbose diagnostic logging is enabled
     */
    public boolean debug() {
        return yaml().getBoolean("debug", false);
    }

    /**
     * @return true when players with {@code honeycomb.bypass} keep their own displays
     */
    /**
     * @return true when the staff only tablist layout may be used
     */
    public boolean staffTablist() {
        return yaml().getBoolean("staff.tablist", false);
    }

    /**
     * @return true when the staff only scoreboard layout may be used
     */
    public boolean staffScoreboard() {
        return yaml().getBoolean("staff.scoreboard", false);
    }

    public boolean respectBypassPermission() {
        return yaml().getBoolean("respect-bypass-permission", true);
    }

    /**
     * @return the default refresh interval in ticks used by displays that do not
     * override it
     */
    public int defaultUpdateInterval() {
        int configured = yaml().getInt("default-update-interval", 20);
        if (configured < 1) {
            report.warning("config.yml", "default-update-interval must be at least 1, using 20");
            return 20;
        }
        return configured;
    }

    /**
     * @return worlds where Honeycomb displays are disabled entirely
     */
    public List<String> disabledWorlds() {
        List<String> worlds = new ArrayList<>();
        for (String entry : yaml().getStringList("disabled-worlds")) {
            if (entry != null && !entry.isBlank()) {
                worlds.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return worlds;
    }

    /**
     * @param worldName world to test
     * @return true when displays are disabled in that world
     */
    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds().contains(worldName.toLowerCase(Locale.ROOT));
    }

    /**
     * @return true when nametags should use a dedicated scoreboard instead of the
     * server main scoreboard (useful to avoid team name collisions)
     */
    public boolean nametagsUseOwnScoreboard() {
        return yaml().getBoolean("nametags.use-dedicated-scoreboard", true);
    }

    /**
     * @return how many ticks between placeholder cache sweeps
     */
    public int cacheSweepInterval() {
        return Math.max(20, yaml().getInt("performance.cache-sweep-interval", 200));
    }

    /**
     * @return true when the plugin should log a warning for placeholders it cannot resolve
     */
    public boolean warnUnknownPlaceholders() {
        return yaml().getBoolean("performance.warn-unknown-placeholders", true);
    }

    /**
     * @return true when other display plugins should be reported in the console
     */
    public boolean detectConflicts() {
        return yaml().getBoolean("conflicts.detect", true);
    }

    /**
     * @return the tablist / scoreboard / nametag owner warning behaviour
     */
    public boolean logConflictAdvice() {
        return yaml().getBoolean("conflicts.log-advice", true);
    }
}
