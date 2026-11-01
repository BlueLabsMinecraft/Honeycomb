package dev.bluelabs.honeycomb.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Number formatting helpers. Kept deliberately small and allocation friendly
 * because several of these run inside display update loops.
 */
public final class Numbers {

    private static final DecimalFormat GROUPED = new DecimalFormat("#,##0");
    private static final String[] COMPACT_SUFFIXES = {"", "k", "M", "B", "T"};

    private Numbers() {
    }

    /**
     * Rounds to a number of decimal places.
     *
     * @param value  the value to round
     * @param places number of decimal places, zero or greater
     * @return the rounded value
     */
    public static double round(double value, int places) {
        if (places < 0) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Formats a double with the given decimal places, without a locale specific
     * decimal separator so the output is stable across machines.
     *
     * @param value  the value
     * @param places decimal places
     * @return the formatted value
     */
    public static String format(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%." + Math.max(0, places) + "f", value);
    }

    /**
     * Formats a whole number with thousands separators.
     *
     * @param value the value
     * @return the grouped value
     */
    public static String grouped(long value) {
        return GROUPED.format(value);
    }

    /**
     * Shortens a large value, for example {@code 12500} becomes {@code 12.5k}.
     *
     * @param value the value
     * @return the compact value
     */
    public static String compact(long value) {
        if (value < 1000L) {
            return Long.toString(value);
        }
        int suffixIndex = 0;
        double scaled = value;
        while (scaled >= 1000.0D && suffixIndex < COMPACT_SUFFIXES.length - 1) {
            scaled /= 1000.0D;
            suffixIndex++;
        }
        String formatted = scaled >= 100.0D
                ? format(scaled, 0)
                : format(scaled, 1);
        if (formatted.endsWith(".0")) {
            formatted = formatted.substring(0, formatted.length() - 2);
        }
        return formatted + COMPACT_SUFFIXES[suffixIndex];
    }

    /**
     * Clamps a value into an inclusive range.
     *
     * @param value the value
     * @param min   lower bound
     * @param max   upper bound
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
