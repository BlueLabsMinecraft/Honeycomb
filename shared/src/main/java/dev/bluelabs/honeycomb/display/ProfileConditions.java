package dev.bluelabs.honeycomb.display;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conditions that decide whether a display profile applies to a player.
 *
 * <p>All configured conditions must pass. An empty condition list matches
 * everyone, which is what makes a {@code priority: 0} default profile work.</p>
 */
public final class ProfileConditions {

    private final List<String> worlds;
    private final List<String> permissions;
    private final List<String> groups;

    private ProfileConditions(List<String> worlds, List<String> permissions, List<String> groups) {
        this.worlds = worlds;
        this.permissions = permissions;
        this.groups = groups;
    }

    /**
     * @return conditions matching every player
     */
    public static ProfileConditions none() {
        return new ProfileConditions(List.of(), List.of(), List.of());
    }

    /**
     * Reads conditions from a {@code conditions} section.
     *
     * <p>World entries may be negated with a leading {@code !}. A name in
     * {@code worlds} may also be a glob such as {@code world_*}.</p>
     *
     * @param section section holding the conditions
     * @param source  file name for warnings
     * @param report  validation report
     * @return the parsed conditions
     */
    public static ProfileConditions parse(ConfigurationSection section, String source, ValidationReport report) {
        if (section == null) {
            return none();
        }
        if (!section.isConfigurationSection("conditions")) {
            return none();
        }
        ConfigurationSection conditions = section.getConfigurationSection("conditions");
        if (conditions == null) {
            return none();
        }
        return new ProfileConditions(
                lowerCase(ConfigValues.strings(conditions, "worlds")),
                ConfigValues.strings(conditions, "permissions"),
                lowerCase(ConfigValues.strings(conditions, "groups"))
        );
    }

    private static List<String> lowerCase(List<String> input) {
        List<String> result = new ArrayList<>(input.size());
        for (String value : input) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    /**
     * @param context match context
     * @return true when every configured condition passes
     */
    public boolean matches(MatchContext context) {
        return matchesWorld(context.worldName()) && matchesGroup(context) && matchesPermissions(context);
    }

    private boolean matchesWorld(String worldName) {
        if (worlds.isEmpty()) {
            return true;
        }
        String name = worldName.toLowerCase(Locale.ROOT);
        boolean hasPositive = false;
        boolean positiveMatch = false;
        for (String entry : worlds) {
            if (entry.startsWith("!")) {
                if (globMatches(entry.substring(1), name)) {
                    return false;
                }
            } else {
                hasPositive = true;
                if (globMatches(entry, name)) {
                    positiveMatch = true;
                }
            }
        }
        return !hasPositive || positiveMatch;
    }

    private boolean matchesGroup(MatchContext context) {
        if (groups.isEmpty()) {
            return true;
        }
        return groups.contains(context.group());
    }

    private boolean matchesPermissions(MatchContext context) {
        for (String permission : permissions) {
            if (permission.startsWith("!")) {
                if (context.hasPermission(permission.substring(1))) {
                    return false;
                }
            } else if (!context.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    private static boolean globMatches(String pattern, String value) {
        if (!pattern.contains("*")) {
            return pattern.equals(value);
        }
        StringBuilder regex = new StringBuilder(pattern.length() + 8);
        for (char character : pattern.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(character)));
            }
        }
        return value.matches(regex.toString());
    }

    /**
     * @return the configured world filters, for diagnostics
     */
    public List<String> worlds() {
        return List.copyOf(worlds);
    }
}
