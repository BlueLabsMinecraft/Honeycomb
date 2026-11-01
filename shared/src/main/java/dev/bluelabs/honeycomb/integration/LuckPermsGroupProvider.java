package dev.bluelabs.honeycomb.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/**
 * Group, prefix and suffix lookup through the LuckPerms API.
 *
 * <p>Only instantiated when LuckPerms is present and enabled, so the class is never
 * loaded on servers without it.</p>
 */
public final class LuckPermsGroupProvider implements GroupProvider {

    private final LuckPerms luckPerms;
    private final boolean usePrefix;
    private final boolean useSuffix;

    /**
     * @param usePrefix whether LuckPerms prefixes are used
     * @param useSuffix whether LuckPerms suffixes are used
     */
    public LuckPermsGroupProvider(boolean usePrefix, boolean useSuffix) {
        this.luckPerms = LuckPermsProvider.get();
        this.usePrefix = usePrefix;
        this.useSuffix = useSuffix;
    }

    @Override
    public String group(Player player) {
        User user = user(player);
        return user == null ? null : user.getPrimaryGroup();
    }

    @Override
    public String prefix(Player player) {
        if (!usePrefix) {
            return null;
        }
        User user = user(player);
        return user == null ? null : user.getCachedData().getMetaData().getPrefix();
    }

    @Override
    public String suffix(Player player) {
        if (!useSuffix) {
            return null;
        }
        User user = user(player);
        return user == null ? null : user.getCachedData().getMetaData().getSuffix();
    }

    @Override
    public int weight(Player player) {
        User user = user(player);
        if (user == null) {
            return 0;
        }
        Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group == null || group.getWeight().isEmpty()) {
            return 0;
        }
        return group.getWeight().getAsInt();
    }

    @Override
    public String sourceName() {
        return "LuckPerms";
    }

    private User user(Player player) {
        return luckPerms.getUserManager().getUser(player.getUniqueId());
    }
}
