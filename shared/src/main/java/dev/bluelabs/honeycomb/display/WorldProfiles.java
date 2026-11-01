package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ValidationReport;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-world overrides loaded from {@code plugins/Honeycomb/worlds/<world>.yml}.
 *
 * <p>A world file may name the layout to use or switch a display off in that world.
 * Keys that are absent fall back to the main configuration files, so a world file
 * only has to describe what is different.</p>
 */
public final class WorldProfiles {

    /** Systems a world file may configure. */
    public static final List<String> SYSTEMS =
            List.of("tablist", "scoreboard", "belowname", "nametags", "bossbars", "sorting");

    /**
     * What one world file says about one system.
     *
     * @param profile layout name, or {@code null} to keep the normal selection
     * @param enabled explicit enabled flag, or {@code null} to keep the normal value
     */
    public record Override(String profile, Boolean enabled) {
    }

    private final ConfigManager configManager;
    private final ValidationReport report;
    private final Map<String, Map<String, Override>> overrides = new HashMap<>();

    /**
     * @param configManager configuration manager
     * @param report        validation report
     */
    public WorldProfiles(ConfigManager configManager, ValidationReport report) {
        this.configManager = configManager;
        this.report = report;
    }

    /**
     * Reads every world file. Replaces any previously loaded overrides.
     */
    public void load() {
        overrides.clear();
        File folder = configManager.worldsFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            return;
        }
        File[] files = folder.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String worldName = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (IOException exception) {
                report.error("worlds/" + fileName, "Could not read file: " + exception.getMessage());
                continue;
            } catch (InvalidConfigurationException exception) {
                report.error("worlds/" + fileName, "Invalid YAML: " + exception.getMessage());
                continue;
            }
            Map<String, Override> perSystem = new HashMap<>();
            for (String system : SYSTEMS) {
                ConfigurationSection section = yaml.getConfigurationSection(system);
                if (section == null) {
                    continue;
                }
                String profile = section.getString("profile");
                Boolean enabled = section.isSet("enabled") ? section.getBoolean("enabled") : null;
                perSystem.put(system, new Override(
                        profile == null || profile.isBlank() ? null : profile,
                        enabled));
            }
            overrides.put(worldName, perSystem);
        }
    }

    /**
     * @param system    system key, one of {@link #SYSTEMS}
     * @param worldName world the player is in
     * @return the override, or {@code null} when the world has no opinion
     */
    public Override override(String system, String worldName) {
        if (worldName == null) {
            return null;
        }
        Map<String, Override> perSystem = overrides.get(worldName.toLowerCase(Locale.ROOT));
        return perSystem == null ? null : perSystem.get(system);
    }

    /**
     * @param system    system key
     * @param worldName world the player is in
     * @return true when the world file explicitly switches the system off
     */
    public boolean isDisabled(String system, String worldName) {
        Override override = override(system, worldName);
        return override != null && Boolean.FALSE.equals(override.enabled());
    }

    /**
     * @param system    system key
     * @param worldName world the player is in
     * @return the layout name the world names, or {@code null}
     */
    public String profileName(String system, String worldName) {
        Override override = override(system, worldName);
        return override == null ? null : override.profile();
    }

    /**
     * @return number of loaded world files, for status output
     */
    public int size() {
        return overrides.size();
    }
}
