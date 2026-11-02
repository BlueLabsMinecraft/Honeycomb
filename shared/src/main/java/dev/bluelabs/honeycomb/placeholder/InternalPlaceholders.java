package dev.bluelabs.honeycomb.placeholder;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.integration.EconomyProvider;
import dev.bluelabs.honeycomb.integration.GroupProvider;
import dev.bluelabs.honeycomb.integration.PlayerStateProvider;
import dev.bluelabs.honeycomb.network.NetworkCounts;
import dev.bluelabs.honeycomb.util.MinecraftVersions;
import dev.bluelabs.honeycomb.util.Numbers;
import dev.bluelabs.honeycomb.util.TimeFormats;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Registers Honeycomb's built-in placeholders.
 *
 * <p>Every value here comes from a live server API call. Nothing is faked: a
 * placeholder whose data source is missing (economy, proxy) simply resolves to
 * {@code null} and stays unresolved in the output.</p>
 */
public final class InternalPlaceholders {

    private final PlaceholderRegistry registry;
    private final ConfigManager configManager;
    private final GroupProvider groups;
    private final PlayerStateProvider states;
    private final EconomyProvider economy;
    private final NetworkCounts network;
    private final long startMillis;

    private String serverName;
    private DateTimeFormatter dateFormat;
    private DateTimeFormatter timeFormat;
    private ZoneId zone;

    /**
     * @param registry      placeholder registry receiving the built-ins
     * @param configManager configuration manager, read for formats
     * @param groups        permission group source
     * @param states        AFK, nickname and vanish source
     * @param economy       economy source
     * @param network       network wide counts
     * @param startMillis   plugin start time, used by {@code %server_uptime%}
     * @param report        report that receives format warnings
     */
    public InternalPlaceholders(PlaceholderRegistry registry,
                                ConfigManager configManager,
                                GroupProvider groups,
                                PlayerStateProvider states,
                                EconomyProvider economy,
                                NetworkCounts network,
                                long startMillis,
                                ValidationReport report) {
        this.registry = registry;
        this.configManager = configManager;
        this.groups = groups;
        this.states = states;
        this.economy = economy;
        this.network = network;
        this.startMillis = startMillis;
        reload(report);
        registerAll();
    }

    /**
     * Re-reads the formatting options from {@code placeholders.yml}.
     *
     * @param report report that receives invalid format warnings
     */
    public void reload(ValidationReport report) {
        YamlConfiguration yaml = configManager.yaml(ConfigType.PLACEHOLDERS);
        String configuredName = yaml.getString("server-name", "");
        if (configuredName != null && !configuredName.isBlank()) {
            this.serverName = configuredName;
        } else if (network.serverName() != null && !network.serverName().isBlank()) {
            this.serverName = network.serverName();
        } else {
            this.serverName = Bukkit.getName();
        }

        String timezone = yaml.getString("timezone", "");
        try {
            this.zone = timezone == null || timezone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            report.warning("placeholders.yml", "Invalid timezone '" + timezone + "', using the server default");
            this.zone = ZoneId.systemDefault();
        }
        this.dateFormat = formatter(yaml.getString("date-format", "dd/MM/yyyy"), "dd/MM/yyyy", report);
        this.timeFormat = formatter(yaml.getString("time-format", "HH:mm:ss"), "HH:mm:ss", report);
    }

    private DateTimeFormatter formatter(String pattern, String fallback, ValidationReport report) {
        try {
            return DateTimeFormatter.ofPattern(pattern == null ? fallback : pattern).withZone(zone);
        } catch (RuntimeException exception) {
            report.warning("placeholders.yml", "Invalid date/time pattern '" + pattern + "', using " + fallback);
            return DateTimeFormatter.ofPattern(fallback).withZone(zone);
        }
    }

    private void registerAll() {
        registerPlayerPlaceholders();
        registerServerPlaceholders();
        registerWorldPlaceholders();
        registerIntegrationPlaceholders();
    }

    private void registerPlayerPlaceholders() {
        registry.register("player_name", context -> {
            Player player = context.player();
            return player == null ? null : player.getName();
        });
        registry.register("player_displayname", context -> {
            Player player = context.player();
            return player == null ? null : PlainTextComponentSerializer.plainText().serialize(player.displayName());
        });
        registry.register("player_uuid", context -> {
            Player player = context.player();
            return player == null ? null : player.getUniqueId().toString();
        });
        registry.register("player_world", context -> {
            Player player = context.player();
            return player == null ? context.worldName() : player.getWorld().getName();
        });
        registry.register("player_ping", context -> {
            Player player = context.player();
            return player == null ? null : Integer.toString(player.getPing());
        });
        registry.register("player_health", context -> {
            Player player = context.player();
            return player == null ? null : Numbers.format(player.getHealth(), 1);
        });
        registry.register("player_max_health", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            double maximum = attribute == null ? 20.0D : attribute.getValue();
            return Numbers.format(maximum, 1);
        });
        registry.register("player_health_percent", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            double maximum = attribute == null ? 20.0D : attribute.getValue();
            if (maximum <= 0.0D) {
                return "0";
            }
            return Integer.toString((int) Math.round(Numbers.clamp(player.getHealth() / maximum, 0.0D, 1.0D) * 100.0D));
        });
        registry.register("player_level", context -> {
            Player player = context.player();
            return player == null ? null : Integer.toString(player.getLevel());
        });
        registry.register("player_food", context -> {
            Player player = context.player();
            return player == null ? null : Integer.toString(player.getFoodLevel());
        });
        registry.register("player_gamemode", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            GameMode mode = player.getGameMode();
            return mode.name().toLowerCase(Locale.ROOT);
        });
        registry.register("player_x", context -> coordinate(context, Coordinate.X));
        registry.register("player_y", context -> coordinate(context, Coordinate.Y));
        registry.register("player_z", context -> coordinate(context, Coordinate.Z));
        registry.register("player_online_time", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return TimeFormats.compact(Duration.ofSeconds(ticks / 20L));
        });
        registry.register("player_playtime_ticks", context -> {
            Player player = context.player();
            return player == null ? null : Long.toString(player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        });
        registry.register("player_biome", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            return player.getWorld()
                    .getBiome(player.getLocation())
                    .getKey()
                    .getKey()
                    .replace('_', ' ');
        });
    }

    private enum Coordinate {
        X,
        Y,
        Z
    }

    private String coordinate(PlaceholderContext context, Coordinate coordinate) {
        Player player = context.player();
        if (player == null) {
            return null;
        }
        int value = switch (coordinate) {
            case X -> player.getLocation().getBlockX();
            case Y -> player.getLocation().getBlockY();
            case Z -> player.getLocation().getBlockZ();
        };
        return Integer.toString(value);
    }

    private void registerServerPlaceholders() {
        registry.register("server_online", context -> Integer.toString(Bukkit.getOnlinePlayers().size()));
        registry.register("server_max_players", context -> Integer.toString(Bukkit.getMaxPlayers()));
        registry.register("server_name", context -> serverName);
        registry.register("server_version", context -> MinecraftVersions.extractMinecraftVersion(Bukkit.getBukkitVersion()));
        registry.register("server_bukkit", context -> Bukkit.getName());
        registry.register("server_tps", context -> {
            double[] tps = Bukkit.getTPS();
            double current = tps.length > 0 ? tps[0] : 20.0D;
            // Paper reports a value above 20 briefly after startup; clamp for display only.
            return Numbers.format(Numbers.clamp(current, 0.0D, 20.0D), 2);
        });
        registry.register("server_mspt", context -> Numbers.format(Bukkit.getAverageTickTime(), 2));
        registry.register("server_memory_used", context -> {
            Runtime runtime = Runtime.getRuntime();
            long used = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
            return Long.toString(used);
        });
        registry.register("server_memory_max", context -> Long.toString(Runtime.getRuntime().maxMemory() / 1_048_576L));
        registry.register("server_memory_percent", context -> {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            long max = runtime.maxMemory();
            if (max <= 0L) {
                return "0";
            }
            return Long.toString(Math.round((double) used / (double) max * 100.0D));
        });
        registry.register("server_uptime", context ->
                TimeFormats.compact(Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startMillis))));
        registry.register("server_uptime_clock", context ->
                TimeFormats.clock(Math.max(0L, System.currentTimeMillis() - startMillis)));
        registry.register("server_time_zone", context -> zone.getId());
    }

    private void registerWorldPlaceholders() {
        registry.register("world", context -> context.worldName());
        registry.register("date", context -> dateFormat.format(Instant.now()));
        registry.register("time", context -> timeFormat.format(Instant.now()));
        registry.register("world_players", context -> {
            String worldName = context.worldName();
            if (worldName == null || worldName.isEmpty()) {
                return null;
            }
            var world = Bukkit.getWorld(worldName);
            return world == null ? null : Integer.toString(world.getPlayers().size());
        });
    }

    /**
     * @param value resolved text, may be null when the integration is absent
     * @return the text, or an empty string so the token disappears cleanly
     */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private void registerIntegrationPlaceholders() {
        registry.register("player_group", context -> {
            Player player = context.player();
            return player == null ? "" : emptyIfNull(groups.group(player));
        });
        registry.register("player_prefix", context -> {
            Player player = context.player();
            return player == null ? "" : emptyIfNull(groups.prefix(player));
        });
        registry.register("player_suffix", context -> {
            Player player = context.player();
            return player == null ? "" : emptyIfNull(groups.suffix(player));
        });
        registry.register("player_afk", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            return Boolean.toString(states.afk(player));
        });
        registry.register("player_afk_tag", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            return states.afk(player) ? "AFK" : "";
        });
        registry.register("player_nickname", context -> {
            Player player = context.player();
            return player == null ? null : states.nickname(player);
        });
        registry.register("player_vanished", context -> {
            Player player = context.player();
            if (player == null) {
                return null;
            }
            return Boolean.toString(states.vanished(player));
        });
        registry.register("econ_balance", context -> {
            Player player = context.player();
            if (player == null || !economy.available()) {
                return null;
            }
            double balance = economy.balance(player);
            if (Double.isNaN(balance)) {
                return null;
            }
            return economy.format(balance);
        });
        registry.register("network_online", context ->
                network.available() ? Integer.toString(Math.max(0, network.onlineEverywhere())) : null);
        registry.register("network_server", context ->
                network.available() ? network.serverName() : null);
        registry.register("network_server_online", context ->
                network.available() ? Integer.toString(Math.max(0, network.onlineHere())) : null);
        registry.register("network_servers", context ->
                network.available() ? Integer.toString(network.servers().size()) : null);
        registry.register("network_proxy", context -> network.proxyName());
    }

    /**
     * @return the placeholders this class registers, for documentation output
     */
    public static List<String> documentedIds() {
        return List.of(
                "player_name", "player_displayname", "player_uuid", "player_world", "player_ping",
                "player_health", "player_max_health", "player_health_percent", "player_level",
                "player_food", "player_gamemode", "player_x", "player_y", "player_z",
                "player_online_time", "player_playtime_ticks", "player_biome",
                "player_group", "player_prefix", "player_suffix", "player_afk", "player_afk_tag",
                "player_nickname", "player_vanished",
                "server_online", "server_max_players", "server_name", "server_version",
                "server_bukkit", "server_tps", "server_mspt", "server_memory_used",
                "server_memory_max", "server_memory_percent", "server_uptime",
                "server_uptime_clock", "server_time_zone",
                "world", "world_players", "date", "time",
                "econ_balance", "network_online", "network_server", "network_server_online",
                "network_servers", "network_proxy"
        );
    }
}
