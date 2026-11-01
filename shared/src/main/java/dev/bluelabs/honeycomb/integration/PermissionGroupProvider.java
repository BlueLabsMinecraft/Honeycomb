package dev.bluelabs.honeycomb.integration;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Group detection for servers without a permissions plugin.
 *
 * <p>A player belongs to a group by holding {@code honeycomb.group.<name>}. The
 * groups and their order come from {@code nametags.yml}, so a server can set up
 * ranks with nothing but a permissions node on a rank plugin.</p>
 */
public final class PermissionGroupProvider implements GroupProvider {

    private final ConfigManager configManager;

    /**
     * @param configManager configuration manager
     */
    public PermissionGroupProvider(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String group(Player player) {
        ConfigurationSection groups = groupSection();
        if (groups == null) {
            return "default";
        }
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (String key : groups.getKeys(false)) {
            ConfigurationSection section = groups.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String permission = section.getString("permission", "honeycomb.group." + key.toLowerCase(Locale.ROOT));
            if (permission == null || permission.isBlank()) {
                continue;
            }
            // isPermissionSet ignores the operator bypass, so an OP is not
            // silently treated as an owner or an admin.
            if (!player.isPermissionSet(permission) || !player.hasPermission(permission)) {
                continue;
            }
            int priority = section.getInt("priority", 0);
            if (best == null || priority > bestPriority) {
                best = key;
                bestPriority = priority;
            }
        }
        if (best != null) {
            return best;
        }
        return null;
    }

    @Override
    public String prefix(Player player) {
        return template(player, "prefix");
    }

    @Override
    public String suffix(Player player) {
        return template(player, "suffix");
    }

    private String template(Player player, String key) {
        String group = group(player);
        if (group == null) {
            return null;
        }
        ConfigurationSection groups = groupSection();
        if (groups == null) {
            return null;
        }
        ConfigurationSection section = groups.getConfigurationSection(group);
        return section == null ? null : section.getString(key);
    }

    @Override
    public int weight(Player player) {
        String group = group(player);
        if (group == null) {
            return 0;
        }
        ConfigurationSection groups = groupSection();
        if (groups == null) {
            return 0;
        }
        ConfigurationSection section = groups.getConfigurationSection(group);
        return section == null ? 0 : section.getInt("priority", 0);
    }

    @Override
    public String sourceName() {
        return "Permissions (fallback)";
    }

    /**
     * @return configured group names, highest priority first
     */
    public List<String> configuredGroups() {
        List<String> names = new ArrayList<>();
        ConfigurationSection groups = groupSection();
        if (groups == null) {
            return names;
        }
        List<ConfigurationSection> sections = new ArrayList<>();
        for (String key : groups.getKeys(false)) {
            ConfigurationSection section = groups.getConfigurationSection(key);
            if (section != null) {
                names.add(key);
                sections.add(section);
            }
        }
        sections.sort(Comparator.comparingInt(section -> -section.getInt("priority", 0)));
        List<String> ordered = new ArrayList<>(names.size());
        for (ConfigurationSection section : sections) {
            ordered.add(section.getName());
        }
        return ordered.isEmpty() ? names : ordered;
    }

    private ConfigurationSection groupSection() {
        ConfigurationSection root = configManager.yaml(ConfigType.NAMETAGS).getConfigurationSection("nametags");
        return root == null ? null : root.getConfigurationSection("groups");
    }
}
