package dev.bluelabs.honeycomb.proxy.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Answers Honeycomb's network questions on a BungeeCord style proxy.
 *
 * <p>The backend plugin asks over {@code honeycomb:network}; this replies with the
 * real player counts and server list the proxy already knows. Without it the
 * backend falls back to the BungeeCord channel, provided the proxy forwards that.</p>
 */
public final class HoneycombBungee extends Plugin implements Listener {

    private static final String CHANNEL = "honeycomb:network";

    @Override
    public void onEnable() {
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("Honeycomb proxy bridge ready on " + CHANNEL);
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag()) || !(event.getSender() instanceof Server server)) {
            return;
        }
        byte[] reply = reply(server, event.getData());
        if (reply != null) {
            server.sendData(CHANNEL, reply);
            event.setCancelled(true);
        }
    }

    private byte[] reply(Server server, byte[] message) {
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
                    for (String name : getProxy().getServers().keySet()) {
                        out.writeUTF(name);
                    }
                }
                case "getserver" -> {
                    out.writeUTF("GetServer");
                    out.writeUTF(server.getInfo().getName());
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
        ProxyServer proxy = getProxy();
        if (scope.isEmpty() || "ALL".equalsIgnoreCase(scope)) {
            return proxy.getOnlineCount();
        }
        ServerInfo info = proxy.getServerInfo(scope);
        return info == null ? -1 : info.getPlayers().size();
    }
}
