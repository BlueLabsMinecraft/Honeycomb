package dev.bluelabs.honeycomb.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Answers Honeycomb's network questions on Velocity.
 *
 * <p>Velocity already knows the player counts and the server list, so the backend
 * only has to ask. Payloads match the BungeeCord format, which keeps one parser on
 * the game server for both proxies.</p>
 */
@Plugin(
        id = "honeycombproxy",
        name = "HoneycombProxy",
        version = "1.1.0",
        description = "Answers Honeycomb network questions with real proxy numbers.",
        authors = {"Blue Labs"})
public final class HoneycombVelocity {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("honeycomb:network");

    private final ProxyServer proxy;

    /**
     * @param proxy the proxy this plugin runs on
     * @param logger plugin logger supplied by Velocity
     */
    @Inject
    public HoneycombVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        proxy.getChannelRegistrar().register(CHANNEL);
        logger.info("Honeycomb proxy bridge ready on {}", CHANNEL.getId());
    }

    /**
     * @param event a plugin message from a backend server
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        byte[] reply = reply(connection, event.getData());
        if (reply != null) {
            connection.sendPluginMessage(CHANNEL, reply);
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    private byte[] reply(ServerConnection connection, byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String sub = in.readUTF();
            String argument = in.available() > 0 ? in.readUTF() : "";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            switch (sub.toLowerCase(java.util.Locale.ROOT)) {
                case "playercount" -> {
                    out.writeUTF("PlayerCount");
                    out.writeUTF(argument.isEmpty() ? "ALL" : argument);
                    out.writeInt(count(argument));
                }
                case "getservers" -> {
                    out.writeUTF("GetServers");
                    for (RegisteredServer server : proxy.getAllServers()) {
                        out.writeUTF(server.getServerInfo().getName());
                    }
                }
                case "getserver" -> {
                    out.writeUTF("GetServer");
                    out.writeUTF(connection.getServerInfo().getName());
                }
                default -> {
                    return null;
                }
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException malformed) {
            return null;
        }
    }

    private int count(String scope) {
        if (scope.isEmpty() || "ALL".equalsIgnoreCase(scope)) {
            return proxy.getPlayerCount();
        }
        Optional<RegisteredServer> server = proxy.getServer(scope);
        return server.map(value -> value.getPlayersConnected().size()).orElse(-1);
    }
}
