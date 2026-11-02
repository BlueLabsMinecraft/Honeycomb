package dev.bluelabs.honeycomb.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One decoded proxy payload.
 *
 * <p>BungeeCord and Velocity share this format: a UTF sub channel followed by
 * arguments. Parsing lives in a value type so the wire format can be tested
 * without a running proxy.</p>
 *
 * @param kind     the sub channel, lower cased
 * @param scope    the server the reply is about, empty for the whole network
 * @param count    the player count, {@code -1} when the reply carries none
 * @param servers  server names, empty unless the reply is a server list
 * @param rawScope the server name reported by {@code GetServer}
 */
public record ProxyReply(String kind, String scope, int count, List<String> servers, String rawScope) {

    /** Sub channel asking for a player count. */
    public static final String PLAYER_COUNT = "playercount";
    /** Sub channel name as it goes on the wire. */
    public static final String WIRE_PLAYER_COUNT = "PlayerCount";
    /** Server list sub channel as it goes on the wire. */
    public static final String WIRE_GET_SERVERS = "GetServers";
    /** Server name sub channel as it goes on the wire. */
    public static final String WIRE_GET_SERVER = "GetServer";
    /** Sub channel asking for the server list. */
    public static final String GET_SERVERS = "getservers";
    /** Sub channel asking which server this is. */
    public static final String GET_SERVER = "getserver";
    /** Sub channel the proxy uses to hand back this server's name. */
    public static final String SERVER_NAME = "getserver";

    /**
     * Decodes a payload.
     *
     * @param message raw bytes from the proxy
     * @return the decoded reply, or {@code null} when the payload is unusable
     */
    public static ProxyReply parse(byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String kind = in.readUTF().toLowerCase(java.util.Locale.ROOT);
            String scope = "";
            int count = -1;
            List<String> servers = new ArrayList<>();
            switch (kind) {
                case PLAYER_COUNT -> {
                    if (in.available() > 0) {
                        scope = safeUtf(in);
                    }
                    if (in.available() >= Integer.BYTES) {
                        count = in.readInt();
                    }
                }
                case GET_SERVERS -> {
                    int guard = 0;
                    while (in.available() > 0 && guard++ < 256) {
                        int before = in.available();
                        String name = safeUtf(in);
                        if (name != null && !name.isBlank()) {
                            servers.add(name);
                        }
                        if (in.available() == before) {
                            break;
                        }
                    }
                }
                case SERVER_NAME -> scope = safeUtf(in);
                default -> {
                    return new ProxyReply(kind, "", -1, List.of(), "");
                }
            }
            return new ProxyReply(kind, scope == null ? "" : scope, count, servers,
                    scope == null ? "" : scope);
        } catch (IOException | RuntimeException malformed) {
            return null;
        }
    }

    private static String safeUtf(DataInputStream in) {
        try {
            return in.readUTF();
        } catch (IOException end) {
            return "";
        }
    }

    /**
     * @return true when this reply answers a player count request
     */
    public boolean isPlayerCount() {
        return PLAYER_COUNT.equals(kind) && count >= 0;
    }

    /**
     * @return true when this reply is the server list
     */
    public boolean isServerList() {
        return GET_SERVERS.equals(kind);
    }
}
