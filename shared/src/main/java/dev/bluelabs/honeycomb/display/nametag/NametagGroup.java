package dev.bluelabs.honeycomb.display.nametag;

import dev.bluelabs.honeycomb.config.ConfigValues;
import dev.bluelabs.honeycomb.config.ValidationReport;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A nametag decoration rule.
 *
 * <p>A group is matched either by permission ({@code honeycomb.group.<name>} by
 * default, or an explicit {@code permission:}) or by the player's LuckPerms group
 * name. That keeps the plugin useful with or without a permissions plugin.</p>
 */
public final class NametagGroup {

    private final String name;
    private final int priority;
    private final String prefixTemplate;
    private final String suffixTemplate;
    private final String permission;

    private NametagGroup(String name, int priority, String prefixTemplate, String suffixTemplate, String permission) {
        this.name = name;
        this.priority = priority;
        this.prefixTemplate = prefixTemplate;
        this.suffixTemplate = suffixTemplate;
        this.permission = permission;
    }

    /**
     * Creates a group registered through the API.
     *
     * @param name       group key
     * @param priority   selection priority
     * @param prefix     prefix template
     * @param suffix     suffix template
     * @param permission permission that grants the group, may be {@code null}
     * @return the group
     */
    public static NametagGroup apiGroup(String name, int priority, String prefix, String suffix, String permission) {
        return new NametagGroup(name, priority, prefix == null ? "" : prefix, suffix == null ? "" : suffix, permission);
    }

    /**
     * @return the group key as written in configuration
     */
    public String name() {
        return name;
    }

    /**
     * @return selection priority; higher wins
     */
    public int priority() {
        return priority;
    }

    /**
     * @return the prefix template
     */
    public String prefixTemplate() {
        return prefixTemplate;
    }

    /**
     * @return the suffix template
     */
    public String suffixTemplate() {
        return suffixTemplate;
    }

    /**
     * @return the permission node that grants this group
     */
    public String permission() {
        return permission;
    }

    /**
     * Reads every group from {@code nametags.yml}.
     *
     * @param root   the nametags section
     * @param report validation report
     * @return the groups in declaration order
     */
    public static List<NametagGroup> loadAll(ConfigurationSection root, ValidationReport report) {
        List<NametagGroup> groups = new ArrayList<>();
        if (root == null) {
            return groups;
        }
        ConfigurationSection section = root.getConfigurationSection("groups");
        if (section == null) {
            report.warning("nametags.yml", "No 'groups' section found under nametags");
            return groups;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection group = section.getConfigurationSection(key);
            if (group == null) {
                report.warning("nametags.yml", "Group '" + key + "' must be a section");
                continue;
            }
            groups.add(new NametagGroup(
                    key,
                    group.getInt("priority", 0),
                    ConfigValues.text(group, "prefix", ""),
                    ConfigValues.text(group, "suffix", ""),
                    ConfigValues.text(group, "permission", "honeycomb.group." + key.toLowerCase(Locale.ROOT))
            ));
        }
        if (groups.isEmpty()) {
            report.warning("nametags.yml", "No nametag groups are defined");
        }
        return groups;
    }

    /**
     * Selects the decoration for a player.
     *
     * <p>Order of precedence: an explicit permission grant, then the player's
     * permissions plugin group name, then the {@code default} group.</p>
     *
     * @param groups      configured groups
     * @param groupName   the player's group name, may be {@code null}
     * @param permissions permission check
     * @return the selected group, or {@code null} when nothing applies
     */
    public static NametagGroup select(List<NametagGroup> groups, String groupName,
                                      Predicate<String> permissions) {
        String wanted = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
        NametagGroup best = null;
        NametagGroup fallback = null;
        for (NametagGroup group : groups) {
            boolean matchesName = group.name.equalsIgnoreCase(wanted);
            boolean matchesPermission = group.permission != null
                    && !group.permission.isBlank()
                    && permissions.test(group.permission);
            if (matchesName || matchesPermission) {
                if (best == null || group.priority > best.priority) {
                    best = group;
                }
            }
            if (fallback == null && group.name.equalsIgnoreCase("default")) {
                fallback = group;
            }
        }
        return best != null ? best : fallback;
    }
}
