package dev.bluelabs.honeycomb.server;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Decides which ping number the displays show.
 *
 * <p>The client measures its own latency, so the number in F3 cannot be changed by
 * a server. What a server does control is every ping Honeycomb draws: the tablist
 * column, the sidebar, an action bar. Those go through {@code %player_ping%}, and
 * with spoofing on that placeholder answers with a made up number, either the same
 * for everyone or a stable per player value inside a range.</p>
 */
public final class PingSpoofer {

    private final ConfigManager configManager;

    private boolean enabled;
    private String mode = "fixed";
    private int value = 24;
    private int min = 15;
    private int max = 45;

    /**
     * @param configManager configuration manager
     */
    public PingSpoofer(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    /**
     * Re-reads the ping section of config.yml.
     */
    public void reload() {
        YamlConfiguration config = configManager.yaml(ConfigType.CONFIG);
        enabled = config.getBoolean("ping.spoof.enabled", false);
        mode = config.getString("ping.spoof.mode", "fixed").toLowerCase(java.util.Locale.ROOT);
        value = config.getInt("ping.spoof.value", 24);
        min = config.getInt("ping.spoof.min", 15);
        max = config.getInt("ping.spoof.max", 45);
        if (max < min) {
            int swap = max;
            max = min;
            min = swap;
        }
    }

    /**
     * @return true when the displayed ping is being replaced
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * The ping to display for a player.
     *
     * @param player player, may be {@code null}
     * @param realPing the value the server measured
     * @return the number Honeycomb draws
     */
    public int display(Player player, int realPing) {
        if (!enabled) {
            return realPing;
        }
        return switch (mode) {
            case "range" -> {
                UUID id = player == null ? new UUID(0L, 0L) : player.getUniqueId();
                int span = max - min + 1;
                yield min + Math.floorMod(id.hashCode(), span);
            }
            case "jitter" -> {
                int span = max - min + 1;
                yield min + (int) (Math.random() * span);
            }
            default -> value;
        };
    }
}
