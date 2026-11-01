package dev.bluelabs.honeycomb.display.scoreboard;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.ProfileConditions;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * One sidebar layout.
 */
public final class ScoreboardProfile extends DisplayProfile {

    /** Minecraft renders at most fifteen sidebar rows. */
    public static final int MAX_LINES = 15;

    private final String title;
    private final List<String> lines;
    private final int updateInterval;
    private final boolean hideNumbers;

    private ScoreboardProfile(String name, int priority, boolean enabled, ProfileConditions conditions,
                              String title, List<String> lines, int updateInterval, boolean hideNumbers) {
        super(name, priority, enabled, conditions);
        this.title = title;
        this.lines = List.copyOf(lines);
        this.updateInterval = updateInterval;
        this.hideNumbers = hideNumbers;
    }

    private static ScoreboardProfile parse(String name, ConfigurationSection section, int defaultInterval,
                                           ValidationReport report) {
        List<String> lines = ConfigValues.lines(section, "lines");
        if (lines.size() > MAX_LINES) {
            report.warning("scoreboards.yml", "Profile '" + name + "' has " + lines.size()
                    + " lines; Minecraft only shows the first " + MAX_LINES);
            lines = lines.subList(0, MAX_LINES);
        }
        if (lines.isEmpty()) {
            report.warning("scoreboards.yml", "Profile '" + name + "' has no lines and was skipped");
        }
        return new ScoreboardProfile(
                name,
                section.getInt("priority", 0),
                section.getBoolean("enabled", true),
                ProfileConditions.parse(section, "scoreboards.yml", report),
                ConfigValues.text(section, "title", "<gradient:#FADE29:#D56B2B><bold>Honeycomb</bold></gradient>"),
                lines,
                ConfigValues.interval(section, "update-interval", defaultInterval, "scoreboards.yml", report),
                section.getBoolean("hide-numbers", true)
        );
    }

    /**
     * Reads every profile from {@code scoreboards.yml}.
     *
     * <p>Two shapes are accepted: profile sections directly under
     * {@code scoreboards} (for example {@code scoreboards.default}) and profile
     * sections nested under {@code scoreboards.profiles}.</p>
     *
     * @param root            the {@code scoreboards} section
     * @param defaultInterval fallback interval
     * @param report          validation report
     * @return the profiles in declaration order
     */
    public static List<ScoreboardProfile> loadAll(ConfigurationSection root, int defaultInterval,
                                                  ValidationReport report) {
        List<ScoreboardProfile> profiles = new ArrayList<>();
        if (root == null) {
            return profiles;
        }
        collect(root, defaultInterval, report, profiles, true);
        ConfigurationSection nested = root.getConfigurationSection("profiles");
        if (nested != null) {
            collect(nested, defaultInterval, report, profiles, false);
        }
        if (profiles.isEmpty()) {
            report.warning("scoreboards.yml", "No scoreboard profile is defined");
        }
        return profiles;
    }

    private static void collect(ConfigurationSection parent, int defaultInterval, ValidationReport report,
                                List<ScoreboardProfile> target, boolean requireLines) {
        for (String key : parent.getKeys(false)) {
            ConfigurationSection section = parent.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (requireLines && !section.isSet("lines")) {
                // A wrapper section such as "profiles" or a shared settings block.
                continue;
            }
            if (!requireLines && !section.isSet("lines")) {
                continue;
            }
            target.add(parse(key, section, defaultInterval, report));
        }
    }

    /**
     * @return the title template
     */
    public String title() {
        return title;
    }

    /**
     * @return the line templates, top to bottom
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * @return ticks between refreshes for this profile
     */
    public int updateInterval() {
        return updateInterval;
    }

    /**
     * @return true when the score numbers next to each line are hidden
     */
    public boolean hideNumbers() {
        return hideNumbers;
    }
}
