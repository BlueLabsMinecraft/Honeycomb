package dev.bluelabs.honeycomb.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The icon picker writes sprite tags, so the tag has to survive the real MiniMessage
 * parser and come out as a component carrying a sprite rather than as literal text.
 */
class IconsTest {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Test
    void spriteTagIsBuiltForAnItemName() {
        String tag = Icons.sprite("diamond");
        assertTrue(tag.contains("minecraft:items"), tag);
        assertTrue(tag.contains("minecraft:item/diamond"), tag);
    }

    @Test
    void fullPathIsKeptAsWritten() {
        assertTrue(Icons.sprite("minecraft:item/oak_log").contains("minecraft:item/oak_log"));
    }

    @Test
    void spriteParsesIntoAnObjectComponent() {
        Component parsed = MINI.deserialize(Icons.sprite("diamond") + " <gray>Diamond");
        String rendered = MiniMessage.miniMessage().serialize(parsed);
        assertTrue(rendered.contains("sprite"), rendered);
        // The literal markup must not leak into the visible text.
        assertFalse(MiniMessage.miniMessage().stripTags(rendered).contains("<sprite"), rendered);
    }

    @Test
    void namesAreNormalised() {
        assertTrue(Icons.sprite(" Gold Ingot ").contains("gold_ingot"));
    }
}
