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

    /** The full editor payload: every section the page can build, decoded by the plugin. */
    @Test
    void everySectionTheEditorCanBuildArrivesIntact() {
        Map<String, Object> decoded = WebCodec.decode("hc1-jVPva9swEP1Xshv5NDuNu5UNEQxZl9JCmsC6tZQ2lLN1SbRYkjkpbULw_z5ku1v6g9Ivtu5Zd_fuvfMOPGaFch7EDiTNcV3URzKYFSRBeF5TBEtCSQziBgYLW8h0kIXn6XQyuj6enn-HCAauZOVJ3IJWhnLGuRfKk3a38AI7kAq1NfIW0s7gYak8pRdLW8Isgrm1_rER4zaVyuWWZY82qMuCeoZ8uFYWuCWOA_HYoCYQ0G2wuxB2oaoiCJmUWWTp3p7OK1-EEs1sF79_Xp5dDscQQaEMuZpMw3JqAiI6gwUTmbTriO-J72wNdwOxpZIUm7XOiF1TPjDJrHMZ8rtpMEpFxouPJyejz8Nv4f112O-nV1TkVhNEkNvCMgi4Ho3H0yuIwPltnXwxHZ_9gAhKtgsm50AkgcFDmyl28Mcq80r3kpVl5bcg-rV0ZahWsrpHHxp6VotFcKbJ39emVq2lFnWe-PABZlVUZ8RojF2bnN7bOmOLMkfn324ukVd39arctK582sNmj_v1dDtmQRKNyngy2HLaC3vP-Gnr5atYoBEnwbIX_8P_74etpdt01yxKdbDTuKkeLzUgiDhpAY0bEEf9fhvOcUWxs3P_gByEuUSjigI7Se8w6SUJPJmkt1L5KtbkHC6oFohJpufDs8mv0WQ4OR7VhmSMRoah_x2eryJtPAg4tYa2udUZRMA0Z3LL2Kt85UB86VdV9Rc");
        assertEquals(List.of("tablist", "scoreboards", "bossbars", "welcome", "maintenance", "brand"),
                List.copyOf(decoded.keySet()));
        Map<String, Object> tablist = WebCodec.flatten(asMap(decoded.get("tablist")));
        assertEquals(true, tablist.get("default.enabled"));
        assertEquals("%player_name%", tablist.get("default.player-list-name"));
        Map<String, Object> maintenance = WebCodec.flatten(asMap(decoded.get("maintenance")));
        assertEquals(true, maintenance.get("maintenance.enabled"));
        assertEquals(500, maintenance.get("motd.max"));
        assertEquals("Vanilla 1.21.11", maintenance.get("motd.fake-software"));
        assertTrue(maintenance.containsKey("maintenance.kick-message"));
        Map<String, Object> welcome = WebCodec.flatten(asMap(decoded.get("welcome")));
        assertEquals("private", welcome.get("join.scope"));
        assertEquals("broadcast", welcome.get("join-announce.scope"));
        Map<String, Object> brand = WebCodec.flatten(asMap(decoded.get("brand")));
        assertEquals(40, brand.get("brand.refresh-ticks"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
