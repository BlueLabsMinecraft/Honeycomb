package dev.bluelabs.honeycomb.display.belowname;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.ProfileConditions;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One below-name layout.
 *
 * <p>Two modes exist: {@code health} uses Minecraft's own health criteria (free,
 * updates client side) and {@code custom} renders a template per player, which
 * costs one write per visible player per viewer.</p>
 */
public final class BelowNameProfile extends DisplayProfile {

    /** How the text under a nametag is produced. */
    public enum Mode {
        HEALTH,
        CUSTOM
    }

    private final Mode mode;
    private final String template;
    private final int updateInterval;

    private BelowNameProfile(String name, int priority, boolean enabled, ProfileConditions conditions,
                             Mode mode, String template, int updateInterval) {
        super(name, priority, enabled, conditions);
        this.mode = mode;
        this.template = template;
        this.updateInterval = updateInterval;
    }

    private static BelowNameProfile parse(String name, ConfigurationSection section, int defaultInterval,
                                          ValidationReport report) {
        String rawMode = ConfigValues.text(section, "mode", "custom").toLowerCase(Locale.ROOT);
        Mode mode;
        if (rawMode.equals("health")) {
            mode = Mode.HEALTH;
        } else if (rawMode.equals("custom")) {
            mode = Mode.CUSTOM;
        } else {
            report.warning("belowname.yml", "Profile '" + name + "' has unknown mode '" + rawMode
                    + "', using custom");
            mode = Mode.CUSTOM;
        }
        return new BelowNameProfile(
                name,
                section.getInt("priority", 0),
                section.getBoolean("enabled", true),
                ProfileConditions.parse(section, "belowname.yml", report),
                mode,
                ConfigValues.text(section, "value", "<red>%player_health%</red> <dark_gray>HP"),
                ConfigValues.interval(section, "update-interval", defaultInterval, "belowname.yml", report)
        );
    }

    /**
     * Reads every profile from {@code belowname.yml}.
     *
     * @param root            the belowname section
     * @param defaultInterval fallback interval
     * @param report          validation report
     * @return the profiles in declaration order
     */
    public static List<BelowNameProfile> loadAll(ConfigurationSection root, int defaultInterval,
                                                 ValidationReport report) {
        List<BelowNameProfile> profiles = new ArrayList<>();
        if (root == null) {
            return profiles;
        }
        profiles.add(parse("default", root, defaultInterval, report));
        ConfigurationSection nested = root.getConfigurationSection("profiles");
        if (nested != null) {
            for (String key : nested.getKeys(false)) {
                ConfigurationSection section = nested.getConfigurationSection(key);
                if (section == null) {
                    report.warning("belowname.yml", "Profile '" + key + "' must be a section");
                    continue;
                }
                profiles.add(parse(key, section, defaultInterval, report));
            }
        }
        return profiles;
    }

    /**
     * @return how the value is produced
     */
    public Mode mode() {
        return mode;
    }

    /**
     * @return the value template used in {@link Mode#CUSTOM} mode
     */
    public String template() {
        return template;
    }

    /**
     * @return ticks between refreshes for this profile
     */
    public int updateInterval() {
        return updateInterval;
    }
}
