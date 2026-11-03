package dev.bluelabs.honeycomb.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser packs the code and the server unpacks it, so a round trip here is the
 * contract between the two.
 */
class WebCodecTest {

    private Map<String, Object> sample() {
        Map<String, Object> tablist = new LinkedHashMap<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("enabled", true);
        entry.put("header", List.of("<gold>Server", "<sprite:\"minecraft:items\":\"minecraft:item/diamond\"> <white>Shop"));
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("worlds", List.of("world"));
        entry.put("conditions", conditions);
        tablist.put("default", entry);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tablist", tablist);
        payload.put("maintenance", Map.of("enabled", false, "motd", Map.of("max", 500)));
        return payload;
    }

    @Test
    void roundTripKeepsEveryField() {
        Map<String, Object> decoded = WebCodec.decode(WebCodec.encode(sample()));
        Map<String, Object> entry = asMap(asMap(decoded.get("tablist")).get("default"));
        assertEquals(true, entry.get("enabled"));
        assertEquals(List.of("world"), asMap(entry.get("conditions")).get("worlds"));
    }

    @Test
    void codeIsUrlSafeAndShort() {
        String code = WebCodec.encode(sample());
        assertTrue(code.startsWith(WebCodec.PREFIX), code);
        assertTrue(code.matches("hc1-[A-Za-z0-9_-]+"), code);
        assertTrue(code.length() < 400, "code length " + code.length());
    }

    @Test
    void flattenTurnsNestingIntoPaths() {
        Map<String, Object> flat = WebCodec.flatten(asMap(sample().get("tablist")));
        assertEquals(true, flat.get("default.enabled"));
        assertEquals(List.of("world"), flat.get("default.conditions.worlds"));
    }

    @Test
    void junkIsRejectedWithAReadableMessage() {
        assertThrows(IllegalArgumentException.class, () -> WebCodec.decode("hello-there"));
        assertThrows(IllegalArgumentException.class, () -> WebCodec.decode(""));
    }

    /**
     * A code produced the way the web page produces one: JSON, raw deflate, base64url.
     * If this passes, whatever the editor prints will import.
     */
    @Test
    void itDecodesACodeTheBrowserWouldPrint() {
        Map<String, Object> decoded = WebCodec.decode("hc1-bY_NCsIwEIRfReZc6rmh9A0KgkfrYW22GshPSbdQKXl304Ie1Nvst8wws0LoZs0kUCs0DzTbXbLPmDWUxJkLPJg0R6gL6nuwummN53N7wrXAEIK8X5GezWjpWfJCbrRcehZcUyrgyHjJob7nP_EuiN6wowWqqqrNcYvkd_gRXybhJVdFPY3RCKsOLpfqIw2i8u2mDj_sqA254HWH5rBNQErpBQ");
        Map<String, Object> tablist = asMap(decoded.get("tablist"));
        Map<String, Object> entry = asMap(tablist.get("default"));
        assertEquals(List.of("<gold>MineSMP"), entry.get("header"));
        Map<String, Object> maintenance = asMap(decoded.get("maintenance"));
        assertEquals(999, asMap(maintenance.get("motd")).get("max"));
    }

    /** A code produced by the web page itself, CompressionStream and all. */
    @Test
    void itDecodesACodeBuiltByThePageItself() {
        Map<String, Object> decoded = WebCodec.decode("hc1-bY5Na4NAEIb_ikyvq1VT2rqIkJaEXNqcS-lhdEezdN0N6wYUu_-9rP2AhFxmXobnHZ4ZHNZKDg74DIJaPKklksZakQDu7IkYHAgFWeDvUHZGiaqsw9ztXzdvz_uXJ2BQdhan6qhwSmjE_qgo0eTgg0FrjPvrBkbIoTFWnGPeM-hRakcadUNnCi2qgRj0xokrakpqijPgy3chSTt-s91uVuvHsB_WaXrFdinlv6Wpmo0OF3879zj66CsqO0ukqyzJs6SI4ii_T1bA4AcDHmfBdgReFEUwry3qxe0_XEg6Gh1w2BlNU2P6GhhYai0Nh9jJ5nMAfpd6778B");
        Map<String, Object> tablist = asMap(asMap(decoded.get("tablist")).get("default"));
        assertEquals(List.of("<gold><bold>HONEYCOMB", "<gray>play.example.net"), tablist.get("header"));
        assertEquals(999, asMap(asMap(decoded.get("maintenance")).get("motd")).get("max"));
        assertEquals(true, asMap(asMap(decoded.get("brand")).get("brand")).get("enabled"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
