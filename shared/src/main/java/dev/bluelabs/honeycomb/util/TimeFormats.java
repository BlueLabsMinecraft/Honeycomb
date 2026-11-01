package dev.bluelabs.honeycomb.util;

import java.time.Duration;

/**
 * Human readable duration formatting used by time based placeholders.
 */
public final class TimeFormats {

    private TimeFormats() {
    }

    /**
     * Formats a duration as a compact string such as {@code 2d 4h 12m}.
     *
     * @param duration the duration to format
     * @return the formatted string, never empty
     */
    public static String compact(Duration duration) {
        long totalSeconds = Math.max(0L, duration.getSeconds());
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append('s');
        return builder.toString().trim();
    }

    /**
     * Formats a millisecond uptime as {@code HH:mm:ss}.
     *
     * @param millis uptime in milliseconds
     * @return the formatted clock style uptime
     */
    public static String clock(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
