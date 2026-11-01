package dev.bluelabs.honeycomb.display.tablist;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.ProfileConditions;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * One tablist layout: header, footer and player list name.
 */
public final class TablistProfile extends DisplayProfile {

    private final List<String> headerLines;
    private final List<String> footerLines;
    private final String playerListNameTemplate;
    private final int updateInterval;

    private TablistProfile(String name, int priority, boolean enabled, ProfileConditions conditions,
                           List<String> headerLines, List<String> footerLines,
                           String playerListNameTemplate, int updateInterval) {
        super(name, priority, enabled, conditions);
        this.headerLines = List.copyOf(headerLines);
        this.footerLines = List.copyOf(footerLines);
        this.playerListNameTemplate = playerListNameTemplate;
        this.updateInterval = updateInterval;
    }

    /**
     * Reads a profile from a configuration section.
     *
     * @param name          profile name
     * @param section       section describing the profile
     * @param defaultInterval interval used when the section does not set one
     * @param report        validation report
     * @return the profile
     */
    public static TablistProfile parse(String name, ConfigurationSection section, int defaultInterval,
                                       ValidationReport report) {
        List<String> header = ConfigValues.lines(section, "header");
        List<String> footer = ConfigValues.lines(section, "footer");
        if (header.isEmpty() && footer.isEmpty() && !section.isSet("player-list-name")) {
            report.warning("tablist.yml", "Tablist profile '" + name + "' defines no header, footer or list name");
        }
        String listName = section.getString("player-list-name");
        int interval = ConfigValues.interval(section, "update-interval", defaultInterval, "tablist.yml", report);
        return new TablistProfile(
                name,
                section.getInt("priority", 0),
                section.getBoolean("enabled", true),
                ProfileConditions.parse(section, "tablist.yml", report),
                header,
                footer,
                listName == null || listName.isBlank() ? null : listName,
                interval
        );
    }

    /**
     * Reads every profile from {@code tablist.yml}.
     *
     * <p>The top level {@code header} and {@code footer} keys form a profile named
     * {@code default}, which keeps the simple single layout configuration valid.
     * Additional layouts live under {@code profiles}.</p>
     *
     * @param root            the tablist section
     * @param defaultInterval fallback interval
     * @param report          validation report
     * @return the profiles in declaration order
     */
    public static List<TablistProfile> loadAll(ConfigurationSection root, int defaultInterval,
                                               ValidationReport report) {
        List<TablistProfile> profiles = new ArrayList<>();
        if (root == null) {
            return profiles;
        }
        boolean hasRootLayout = !ConfigValues.lines(root, "header").isEmpty()
                || !ConfigValues.lines(root, "footer").isEmpty()
                || root.isSet("player-list-name");
        if (hasRootLayout) {
            profiles.add(parse("default", root, defaultInterval, report));
        }
        ConfigurationSection profilesSection = root.getConfigurationSection("profiles");
        if (profilesSection != null) {
            for (String key : profilesSection.getKeys(false)) {
                ConfigurationSection section = profilesSection.getConfigurationSection(key);
                if (section == null) {
                    report.warning("tablist.yml", "Profile '" + key + "' must be a section");
                    continue;
                }
                profiles.add(parse(key, section, defaultInterval, report));
            }
        }
        if (profiles.isEmpty()) {
            report.warning("tablist.yml", "No tablist profile is defined");
        }
        return profiles;
    }

    /**
     * @return header template lines
     */
    public List<String> headerLines() {
        return headerLines;
    }

    /**
     * @return footer template lines
     */
    public List<String> footerLines() {
        return footerLines;
    }

    /**
     * @return the player list name template, or {@code null} when names are left alone
     */
    public String playerListNameTemplate() {
        return playerListNameTemplate;
    }

    /**
     * @return ticks between refreshes for this profile
     */
    public int updateInterval() {
        return updateInterval;
    }
}
