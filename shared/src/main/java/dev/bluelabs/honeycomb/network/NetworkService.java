package dev.bluelabs.honeycomb.network;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network wide player counts obtained from a proxy over plugin messaging.
 *
 * <p>BungeeCord, Waterfall and Velocity all speak the same payloads on
 * {@code bungeecord:main}, so one implementation serves all three. The proxy type
 * is detected from the server's own configuration when {@code type: auto}.</p>
 *
 * <p>Nothing is invented here: until a proxy actually answers, {@link #available()}
 * is false and the network placeholders stay unresolved.</p>
 */
public final class NetworkService implements NetworkCounts {

    private static final String HONEYCOMB_CHANNEL = "honeycomb:network";
    private static final String MODERN_CHANNEL = "bungeecord:main";
    private static final String LEGACY_CHANNEL = "BungeeCord";
    private static final String TASK_NAME = "network-counts";
    private static final long STALE_MILLIS = 60_000L;

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ValidationReport report;
    private final TaskRegistry tasks;
    private final Map<String, Integer> serverCounts = new ConcurrentHashMap<>();
    private final Set<String> serverNames = new LinkedHashSet<>();
    private final ProxyMessageListener listener = new ProxyMessageListener(this);
    private final List<String> registeredChannels = new ArrayList<>();

    private ProxyType proxyType = ProxyType.NONE;
    private boolean enabled;
    private int totalOnline = -1;
    private long lastAnswer;
    private long lastRequest;
    private String serverName = "";
    private int intervalTicks = 200;
    private boolean askServerList = true;

    /**
     * @param plugin        plugin instance
     * @param configManager configuration manager
     * @param report        validation report
     * @param tasks         task registry
     */
    public NetworkService(Plugin plugin, ConfigManager configManager, ValidationReport report, TaskRegistry tasks) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.report = report;
        this.tasks = tasks;
    }

    /**
     * Reads configuration and starts polling when enabled.
     */
    public void enable() {
        disable();
        ConfigurationSection root = configManager.yaml(ConfigType.NETWORK).getConfigurationSection("network");
        this.enabled = root != null && root.getBoolean("enabled", false);
        this.proxyType = ProxyType.of(root == null ? "auto" : root.getString("type", "auto"));
        this.serverName = root == null ? "" : String.valueOf(root.getString("server-name", ""));
        this.askServerList = root == null || root.getBoolean("ask-server-list", true);
        this.intervalTicks = Math.max(20, (root == null ? 10 : root.getInt("request-interval-seconds", 10)) * 20);
        if (this.serverName == null) {
            this.serverName = "";
        }
        if (!this.enabled || this.proxyType == ProxyType.NONE) {
            this.enabled = false;
            return;
        }
        try {
            register(plugin.getServer().getMessenger()::registerOutgoingPluginChannel,
                    plugin.getServer().getMessenger()::registerIncomingPluginChannel);
        } catch (RuntimeException exception) {
            report.error("network.yml", "Could not register proxy channels: " + exception.getMessage());
            this.enabled = false;
            return;
        }
        plugin.getLogger().info("Proxy support on: " + proxyType.label()
                + " over " + String.join(", ", registeredChannels));
        tasks.repeating(TASK_NAME, 40L, intervalTicks, this::requestCounts);
        requestCounts();
    }

    private void register(java.util.function.BiConsumer<Plugin, String> outgoing,
                          PluginChannelRegistrar incoming) {
        for (String channel : List.of(HONEYCOMB_CHANNEL, MODERN_CHANNEL, LEGACY_CHANNEL)) {
            try {
                outgoing.accept(plugin, channel);
                incoming.accept(plugin, channel, listener);
                registeredChannels.add(channel);
            } catch (RuntimeException alreadyRegistered) {
                report.warning("network.yml", "Channel " + channel + " was already registered elsewhere");
            }
        }
    }

    /**
     * Receives the three argument form for an incoming channel.
     */
    @FunctionalInterface
    public interface PluginChannelRegistrar {
        /**
         * @param plugin   plugin instance
         * @param channel  channel name
         * @param listener listener to notify
         */
        void accept(Plugin plugin, String channel, org.bukkit.plugin.messaging.PluginMessageListener listener);
    }

    /**
     * Stops polling and releases the plugin channels.
     */
    public void disable() {
        tasks.cancel(TASK_NAME);
        for (String channel : registeredChannels) {
            try {
                plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
                plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel, listener);
            } catch (RuntimeException ignored) {
                // Channel was already unregistered by the server shutting down.
            }
        }
        registeredChannels.clear();
        serverCounts.clear();
        serverNames.clear();
        totalOnline = -1;
        lastAnswer = 0L;
    }

    /**
     * Asks the proxy for the network wide player count.
     */
    public void requestCounts() {
        if (!enabled) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }
        send(carrier, ProxyReply.WIRE_PLAYER_COUNT, "ALL");
        if (askServerList) {
            send(carrier, ProxyReply.WIRE_GET_SERVERS);
        }
        if (serverName.isEmpty()) {
            send(carrier, ProxyReply.WIRE_GET_SERVER);
        }
        for (String known : List.copyOf(serverNames)) {
            send(carrier, ProxyReply.WIRE_PLAYER_COUNT, known);
        }
        lastRequest = System.currentTimeMillis();
    }

    private void send(Player carrier, String... parts) {
        if (registeredChannels.isEmpty()) {
            return;
        }
        for (String channel : List.copyOf(registeredChannels)) {
            sendOn(carrier, channel, parts);
        }
    }

    private void sendOn(Player carrier, String channel, String... parts) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (String part : parts) {
                out.writeUTF(part);
            }
            carrier.sendPluginMessage(plugin, channel, bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            report.warning("network.yml", "Could not send proxy request: " + exception.getMessage());
        }
    }

    /**
     * Parses a proxy reply. Exposed so the payload handling can be tested.
     *
     * @param channel the channel the message arrived on
     * @param message raw payload
     */
    public void handle(String channel, byte[] message) {
        ProxyReply reply = ProxyReply.parse(message);
        if (reply == null) {
            report.warning("network.yml", "Unreadable proxy reply on " + channel);
            return;
        }
        if (reply.isPlayerCount()) {
            if (reply.scope().isEmpty() || "ALL".equalsIgnoreCase(reply.scope())) {
                totalOnline = Math.max(0, reply.count());
            } else {
                serverCounts.put(reply.scope(), Math.max(0, reply.count()));
                serverNames.add(reply.scope());
            }
            lastAnswer = System.currentTimeMillis();
        } else if (reply.isServerList()) {
            serverNames.clear();
            serverNames.addAll(reply.servers());
            lastAnswer = System.currentTimeMillis();
        } else if (ProxyReply.GET_SERVER.equals(reply.kind()) && !reply.rawScope().isBlank()) {
            serverName = reply.rawScope();
        }
    }

    @Override
    public boolean available() {
        return enabled && totalOnline >= 0 && System.currentTimeMillis() - lastAnswer < STALE_MILLIS;
    }

    @Override
    public int onlineEverywhere() {
        return available() ? totalOnline : -1;
    }

    @Override
    public int onlineOn(String name) {
        if (name == null) {
            return -1;
        }
        Integer count = serverCounts.get(name);
        return count == null ? -1 : count;
    }

    @Override
    public int onlineHere() {
        String own = serverName();
        if (own != null && !own.isBlank()) {
            int here = onlineOn(own);
            if (here >= 0) {
                return here;
            }
        }
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public String serverName() {
        if (serverName != null && !serverName.isBlank()) {
            return serverName;
        }
        return String.valueOf(configManager.yaml(ConfigType.NETWORK).getString("network.server-name", ""));
    }

    @Override
    public String proxyName() {
        return proxyType.label();
    }

    @Override
    public List<String> servers() {
        return List.copyOf(serverNames);
    }

    @Override
    public String sourceName() {
        return enabled ? proxyType.label() + " (" + String.join(", ", registeredChannels) + ")"
                : "Disabled";
    }

    /**
     * @return true when proxy support is enabled in configuration
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return the configured proxy type
     */
    public ProxyType proxyType() {
        return proxyType;
    }

    /**
     * @return the tick when the last request was sent, for diagnostics
     */
    public long lastRequestMillis() {
        return lastRequest;
    }

    /**
     * @return the server counts collected so far, for diagnostics
     */
    public Map<String, Integer> knownServerCounts() {
        return Collections.unmodifiableMap(serverCounts);
    }
}
