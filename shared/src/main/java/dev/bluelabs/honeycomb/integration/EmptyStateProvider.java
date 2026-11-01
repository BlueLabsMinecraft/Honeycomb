package dev.bluelabs.honeycomb.integration;

import org.bukkit.entity.Player;

/**
 * State provider used when no plugin can answer: AFK is false, no nickname exists
 * and nobody is considered vanished.
 */
public final class EmptyStateProvider implements PlayerStateProvider {

    @Override
    public boolean afk(Player player) {
        return false;
    }

    @Override
    public String nickname(Player player) {
        return null;
    }

    @Override
    public boolean vanished(Player player) {
        return false;
    }

    @Override
    public String sourceName() {
        return "";
    }
}
