package dev.bluelabs.honeycomb.network;

import dev.bluelabs.honeycomb.config.ConfigManager;
import dev.bluelabs.honeycomb.config.ConfigType;
import dev.bluelabs.honeycomb.config.ValidationReport;
import dev.bluelabs.honeycomb.scheduler.TaskRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network wide player counts obtained from a proxy over plugin messaging.
 *
 * <p>Nothing is invented here: until a proxy actually answers, {@link #available()}
 * is false and the network placeholders stay unresolved. Configure it in
 * {@code network.yml}; on a standalone server it stays off.</p>
 */
public final class NetworkService implements NetworkCounts {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String VELOCITY_CHANNEL = "bungeecord:main";
    private static final String TASK_NAME = "network-counts";

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ValidationReport report;
    private final TaskRegistry tasks;
    private final Map<String, Integer> serverCounts = new ConcurrentHashMap<>();
    private final ProxyMessageListener listener = new ProxyMessageListener(this);

    private boolean enabled;
    private int totalOnline = -1;
    private long lastAnswer;
    private long lastRequest;
    private String serverName = "";
    private int intervalTicks = 200;
    private final List<String> registeredChannels = new ArrayList<>();

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
        String type = root == null ? "bungee" : root.getString("type", "bungee");
        String configuredName = root == null ? "" : root.getString("server-name", "");
        this.serverName = configuredName == null ? "" : configuredName;
        this.intervalTicks = Math.max(20, (root == null ? 10 : root.getInt("request-interval-seconds", 10)) * 20);
        if (!this.enabled) {
            return;
        }
        String normalisedType = type == null ? "bungee" : type.toLowerCase(Locale.ROOT);
        if (normalisedType.equals("none")) {
            this.enabled = false;
            return;
        }
        try {
            List<String> channels = new ArrayList<>();
            channels.add(BUNGEE_CHANNEL);
            if (!normalisedType.equals("bungee")) {
                channels.add(VELOCITY_CHANNEL);
            }
            for (String channel : channels) {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
                plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, listener);
                registeredChannels.add(channel);
            }
        } catch (RuntimeException exception) {
            report.error("network.yml", "Could not register proxy channels: " + exception.getMessage());
            this.enabled = false;
            return;
        }
        tasks.repeating(TASK_NAME, 40L, intervalTicks, this::requestCounts);
        requestCounts();
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
        send(carrier, "PlayerCount", "ALL");
        if (serverName.isEmpty()) {
            send(carrier, "GetServer");
        }
        lastRequest = System.currentTimeMillis();
    }

    private void send(Player carrier, String... parts) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (String part : parts) {
                out.writeUTF(part);
            }
            carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, bytes.toByteArray());
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
        if (message == null || message.length == 0) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subChannel = in.readUTF();
            if ("PlayerCount".equalsIgnoreCase(subChannel)) {
                handlePlayerCount(in);
            } else if ("GetServer".equalsIgnoreCase(subChannel)) {
                String name = in.readUTF();
                if (name != null && !name.isBlank()) {
                    serverName = name;
                }
            }
        } catch (IOException exception) {
            report.warning("network.yml", "Malformed proxy reply on " + channel);
        }
    }

    private void handlePlayerCount(DataInputStream in) throws IOException {
        String scope = "";
        if (in.available() > 0) {
            try {
                scope = in.readUTF();
            } catch (IOException ignored) {
                scope = "";
            }
        }
        if (in.available() >= Integer.BYTES) {
            int count = in.readInt();
            if (scope.isEmpty() || "ALL".equalsIgnoreCase(scope)) {
                totalOnline = Math.max(0, count);
            } else {
                serverCounts.put(scope, Math.max(0, count));
            }
            lastAnswer = System.currentTimeMillis();
        }
    }

    @Override
    public boolean available() {
        return enabled && totalOnline >= 0 && System.currentTimeMillis() - lastAnswer < 60_000L;
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
    public String serverName() {
        if (serverName != null && !serverName.isBlank()) {
            return serverName;
        }
        return configManager.yaml(ConfigType.NETWORK).getString("network.server-name", "");
    }

    @Override
    public String sourceName() {
        return enabled ? "Proxy (" + String.join(", ", registeredChannels) + ")" : "Disabled";
    }

    /**
     * @return true when proxy support is enabled in configuration
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return the tick when the last request was sent, for diagnostics
     */
    public long lastRequestMillis() {
        return lastRequest;
    }
}
