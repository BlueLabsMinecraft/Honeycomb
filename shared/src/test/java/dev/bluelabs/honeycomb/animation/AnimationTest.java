package dev.bluelabs.honeycomb.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationTest {

    @Test
    void advancesOneFramePerInterval() {
        Animation animation = new Animation("test", 5, List.of("a", "b", "c"), true);
        assertEquals("a", animation.frameAt(0));
        assertEquals("a", animation.frameAt(4));
        assertEquals("b", animation.frameAt(5));
        assertEquals("c", animation.frameAt(10));
        assertEquals("a", animation.frameAt(15));
    }

    @Test
    void holdsTheLastFrameWhenNotLooping() {
        Animation animation = new Animation("once", 2, List.of("a", "b"), false);
        assertEquals("b", animation.frameAt(100));
        assertEquals(1, animation.frameIndexAt(Long.MAX_VALUE / 2));
    }

    @Test
    void intervalIsAtLeastOne() {
        Animation animation = new Animation("fast", 0, List.of("a", "b"), true);
        assertEquals(1, animation.interval());
        assertEquals("b", animation.frameAt(1));
    }

    @Test
    void rejectsEmptyFrames() {
        assertThrows(IllegalArgumentException.class, () -> new Animation("bad", 1, List.of(), true));
    }

    @Test
    void negativeTicksDoNotThrow() {
        Animation animation = new Animation("test", 5, List.of("a", "b"), true);
        assertEquals(0, animation.frameIndexAt(-5L));
    }
}
