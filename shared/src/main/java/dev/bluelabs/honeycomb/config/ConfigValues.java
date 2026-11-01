package dev.bluelabs.honeycomb.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Small tolerant readers for configuration values.
 *
 * <p>Server owners write {@code header: "text"} as often as a YAML list, so every
 * list reader accepts both instead of silently returning an empty list.</p>
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * Reads a list of lines, accepting a single scalar as a one line list.
     *
     * @param section parent section
     * @param path    value path
     * @return the lines, never {@code null}
     */
    public static List<String> lines(ConfigurationSection section, String path) {
        List<String> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        if (section.isList(path)) {
            for (Object entry : section.getList(path, List.of())) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
            return result;
        }
        String single = section.getString(path);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    /**
     * Reads a list of plain strings.
     *
     * @param section parent section
     * @param path    value path
     * @return the values, never {@code null}
     */
    public static List<String> strings(ConfigurationSection section, String path) {
        return lines(section, path);
    }

    /**
     * Reads a tick interval, falling back safely and reporting the problem.
     *
     * @param section  parent section
     * @param path     value path
     * @param fallback value used when missing or invalid
     * @param source   file name used in the report
     * @param report   validation report
     * @return the interval, at least one
     */
    public static int interval(ConfigurationSection section, String path, int fallback, String source,
                               ValidationReport report) {
        int value = section == null ? fallback : section.getInt(path, fallback);
        if (value < 1) {
            report.warning(source, path + " must be at least 1, using " + fallback);
            return fallback;
        }
        return value;
    }

    /**
     * Reads a decimal value with a fallback.
     *
     * @param section  parent section
     * @param path     value path
     * @param fallback value used when missing
     * @return the value
     */
    public static double decimal(ConfigurationSection section, String path, double fallback) {
        return section == null ? fallback : section.getDouble(path, fallback);
    }

    /**
     * Reads text with a fallback for missing or blank values.
     *
     * @param section  parent section
     * @param path     value path
     * @param fallback value used when missing or blank
     * @return the value
     */
    public static String text(ConfigurationSection section, String path, String fallback) {
        String value = section == null ? null : section.getString(path);
        return value == null || value.isBlank() ? fallback : value;
    }
}
