package dev.bluelabs.honeycomb.animation;

import java.util.Collections;
import java.util.List;

/**
 * A frame based animation.
 *
 * <p>Deliberately free of Bukkit types: the frame for any tick can be computed
 * directly, so one animation can drive hundreds of players without state.</p>
 */
public final class Animation {

    private final String id;
    private final int interval;
    private final List<String> frames;
    private final boolean loop;

    /**
     * @param id       animation id used in templates as {@code <anim:id>}
     * @param interval ticks between frames, one or greater
     * @param frames   frame templates, must not be empty
     * @param loop     whether the animation repeats after the last frame
     */
    public Animation(String id, int interval, List<String> frames, boolean loop) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Animation '" + id + "' needs at least one frame");
        }
        this.id = id;
        this.interval = Math.max(1, interval);
        this.frames = List.copyOf(frames);
        this.loop = loop;
    }

    /**
     * @return the animation id
     */
    public String id() {
        return id;
    }

    /**
     * @return ticks between frames
     */
    public int interval() {
        return interval;
    }

    /**
     * @return the immutable frame list
     */
    public List<String> frames() {
        return Collections.unmodifiableList(frames);
    }

    /**
     * @return true when the animation restarts after the last frame
     */
    public boolean loop() {
        return loop;
    }

    /**
     * @return number of frames
     */
    public int frameCount() {
        return frames.size();
    }

    /**
     * Selects the frame index for a tick.
     *
     * @param tick global tick counter
     * @return index inside the frame list, or {@code -1} when there are no frames
     */
    public int frameIndexAt(long tick) {
        // Kept in long arithmetic: a server that has been up for months must not
        // overflow the frame counter.
        long step = Math.max(0L, tick) / (long) interval;
        if (loop) {
            return (int) (step % frames.size());
        }
        return (int) Math.min(step, (long) frames.size() - 1L);
    }

    /**
     * Selects the raw frame template for a tick.
     *
     * @param tick global tick counter
     * @return the frame template
     */
    public String frameAt(long tick) {
        return frames.get(frameIndexAt(tick));
    }
}
