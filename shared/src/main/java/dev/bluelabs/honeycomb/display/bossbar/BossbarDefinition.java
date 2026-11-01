package dev.bluelabs.honeycomb.display.bossbar;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.display.DisplayProfile;
import dev.bluelabs.honeycomb.display.ProfileConditions;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One bossbar definition from {@code bossbars.yml}.
 */
public final class BossbarDefinition extends DisplayProfile {

    private final String titleTemplate;
    private final BossBar.Color color;
    private final BossBar.Overlay overlay;
    private final String progressSetting;
    private final int durationSeconds;
    private final boolean countdown;

    private BossbarDefinition(String name, int priority, boolean enabled, ProfileConditions conditions,
                              String titleTemplate, BossBar.Color color, BossBar.Overlay overlay,
                              String progressSetting, int durationSeconds, boolean countdown) {
        super(name, priority, enabled, conditions);
        this.titleTemplate = titleTemplate;
        this.color = color;
        this.overlay = overlay;
        this.progressSetting = progressSetting;
        this.durationSeconds = durationSeconds;
        this.countdown = countdown;
    }

    /**
     * Reads every definition from the {@code bossbars} section.
     *
     * @param root   the bossbars section
     * @param report validation report
     * @return the definitions in declaration order
     */
    public static List<BossbarDefinition> loadAll(ConfigurationSection root, ValidationReport report) {
        List<BossbarDefinition> definitions = new ArrayList<>();
        if (root == null) {
            return definitions;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (!section.isSet("title")) {
                report.warning("bossbars.yml", "Bossbar '" + key + "' has no title and was skipped");
                continue;
            }
            Object progressRaw = section.get("progress");
            String progress = progressRaw == null ? "1.0" : String.valueOf(progressRaw);
            definitions.add(new BossbarDefinition(
                    key,
                    section.getInt("priority", 0),
                    section.getBoolean("enabled", true),
                    ProfileConditions.parse(section, "bossbars.yml", report),
                    ConfigValues.text(section, "title", ""),
                    parseColor(ConfigValues.text(section, "color", "BLUE"), key, report),
                    parseOverlay(ConfigValues.text(section, "style", "SOLID"), key, report),
                    progress,
                    Math.max(0, section.getInt("duration", 0)),
                    section.getBoolean("countdown", false)
            ));
        }
        return definitions;
    }

    private static BossBar.Color parseColor(String value, String id, ValidationReport report) {
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (BossBar.Color candidate : BossBar.Color.values()) {
            if (candidate.name().equals(normalised)) {
                return candidate;
            }
        }
        report.warning("bossbars.yml", "Bossbar '" + id + "' has unknown color '" + value + "', using BLUE");
        return BossBar.Color.BLUE;
    }

    private static BossBar.Overlay parseOverlay(String value, String id, ValidationReport report) {
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        // "SOLID" is the friendly name used in the documentation for PROGRESS.
        if (normalised.equals("SOLID") || normalised.equals("PROGRESS")) {
            return BossBar.Overlay.PROGRESS;
        }
        for (BossBar.Overlay candidate : BossBar.Overlay.values()) {
            if (candidate.name().equals(normalised)) {
                return candidate;
            }
        }
        report.warning("bossbars.yml", "Bossbar '" + id + "' has unknown style '" + value + "', using SOLID");
        return BossBar.Overlay.PROGRESS;
    }

    /**
     * @return the title template
     */
    public String titleTemplate() {
        return titleTemplate;
    }

    /**
     * @return the bar colour
     */
    public BossBar.Color color() {
        return color;
    }

    /**
     * @return the bar overlay style
     */
    public BossBar.Overlay overlay() {
        return overlay;
    }

    /**
     * @return the raw progress setting: a number between 0 and 1, or a placeholder
     */
    public String progressSetting() {
        return progressSetting;
    }

    /**
     * @return seconds before the bar is removed automatically, zero for permanent
     */
    public int durationSeconds() {
        return durationSeconds;
    }

    /**
     * @return true when the bar drains over its duration
     */
    public boolean countdown() {
        return countdown;
    }
}
