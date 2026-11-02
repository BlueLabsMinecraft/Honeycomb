package dev.bluelabs.honeycomb.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyReplyTest {

    private static byte[] payload(String... parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (String part : parts) {
            out.writeUTF(part);
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] playerCount(String scope, int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("PlayerCount");
        out.writeUTF(scope);
        out.writeInt(count);
        out.flush();
        return bytes.toByteArray();
    }

    @Test
    void readsNetworkWideCount() throws IOException {
        ProxyReply reply = ProxyReply.parse(playerCount("ALL", 37));
        assertTrue(reply.isPlayerCount());
        assertEquals("ALL", reply.scope());
        assertEquals(37, reply.count());
    }

    @Test
    void readsPerServerCount() throws IOException {
        ProxyReply reply = ProxyReply.parse(playerCount("survival", 4));
        assertEquals("survival", reply.scope());
        assertEquals(4, reply.count());
        assertFalse(reply.isServerList());
    }

    @Test
    void readsServerList() throws IOException {
        ProxyReply reply = ProxyReply.parse(payload("GetServers", "lobby", "survival", "prison"));
        assertTrue(reply.isServerList());
        assertEquals(List.of("lobby", "survival", "prison"), reply.servers());
    }

    @Test
    void readsOwnServerName() throws IOException {
        ProxyReply reply = ProxyReply.parse(payload("GetServer", "skyblock-1"));
        assertEquals("skyblock-1", reply.rawScope());
    }

    @Test
    void ignoresUnknownSubChannel() throws IOException {
        ProxyReply reply = ProxyReply.parse(payload("SomethingElse", "x"));
        assertFalse(reply.isPlayerCount());
        assertEquals(-1, reply.count());
    }

    @Test
    void refusesBrokenPayload() {
        assertNull(ProxyReply.parse(null));
        assertNull(ProxyReply.parse(new byte[0]));
        assertNull(ProxyReply.parse(new byte[]{0x00, 0x05, 0x41}));
    }

    @Test
    void detectsConfiguredTypes() {
        assertEquals(ProxyType.VELOCITY, ProxyType.of("velocity"));
        assertEquals(ProxyType.BUNGEE, ProxyType.of("waterfall"));
        assertEquals(ProxyType.NONE, ProxyType.of("none"));
    }
}
