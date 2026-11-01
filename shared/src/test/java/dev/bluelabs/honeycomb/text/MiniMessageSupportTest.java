package dev.bluelabs.honeycomb.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MiniMessage is the only markup Honeycomb accepts, so these tests pin the tags the
 * shipped default files rely on: gradients, the shadow tag and nesting.
 */
class MiniMessageSupportTest {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private String plain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(input));
    }

    @Test
    void shadowTagIsAppliedWithoutLeakingMarkup() {
        String input = "<shadow:#000000:1><gradient:#FADE29:#D56B2B><bold>HONEYCOMB</bold></gradient></shadow>";
        String text = plain(input);
        assertEquals("HONEYCOMB", text);
        assertFalse(text.contains("shadow"), "the shadow tag must not survive as text");
    }

    @Test
    void shadowTagAloneWorks() {
        assertEquals("Hello", plain("<shadow:#000000:1>Hello</shadow>"));
    }

    @Test
    void nestedTagsKeepTheirOrder() {
        Component component = MINI.deserialize("<shadow:#000000:0.5><aqua><bold>Blue</bold></aqua></shadow>");
        assertEquals("Blue", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void unknownTagsDoNotBreakRendering() {
        String text = plain("<not_a_real_tag>safe</not_a_real_tag>");
        assertTrue(text.contains("safe"));
    }
}
