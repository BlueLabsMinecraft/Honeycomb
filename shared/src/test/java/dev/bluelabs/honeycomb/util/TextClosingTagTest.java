package dev.bluelabs.honeycomb.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing tags are the thing admins get wrong most often, either by leaving one
 * out or by pasting one that has nothing to close.
 */
class TextClosingTagTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void closingTagWorks() {
        Component parsed = Text.parse("<gray>hello</gray>");
        assertEquals("hello", plain(parsed));
        assertEquals(NamedTextColor.GRAY, parsed.color());
    }

    @Test
    void missingClosingTagStillColours() {
        Component parsed = Text.parse("<yellow>hello");
        assertEquals("hello", plain(parsed));
        assertTrue(parsed.toString().toLowerCase().contains("yellow"));
    }

    @Test
    void strayClosingTagIsDropped() {
        Component parsed = Text.parse("hello</gray>");
        assertEquals("hello", plain(parsed));
        assertFalse(plain(parsed).contains("gray"));
    }

    @Test
    void closingOnlyLineIsEmptyText() {
        assertEquals("", plain(Text.parse("</gray>")));
    }

    @Test
    void mismatchedNestingIsRepaired() {
        Component parsed = Text.parse("<red><gray>hello</red>");
        assertEquals("hello", plain(parsed));
    }

    @Test
    void shadowAndGradientCloseCleanly() {
        Component parsed = Text.parse("<shadow:#000000:1><gradient:#FFD34A:#E28000>hi</gradient></shadow>");
        assertEquals("hi", plain(parsed));
    }

    @Test
    void legacyCodesStillConvert() {
        Component parsed = Text.parse("&ehello");
        assertEquals("hello", plain(parsed));
        assertTrue(Text.sanitize("&ehello").contains("<yellow>"));
    }

    @Test
    void unknownTagIsDroppedNotPrinted() {
        assertEquals("hello", plain(Text.parse("<notatag>hello")));
        assertEquals("hello", plain(Text.parse("<yellow text>hello")));
    }
}
