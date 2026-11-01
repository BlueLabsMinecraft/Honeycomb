package dev.bluelabs.honeycomb.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and compares Minecraft server version strings.
 *
 * <p>Honeycomb never refuses to start because of a version string it does not
 * recognise: unknown versions are reported as warnings so a server owner can
 * still use the plugin after a Minecraft update.</p>
 */
public final class MinecraftVersions {

    /** Versions this source tree is built and documented for. */
    private static final List<String> TESTED_RANGES = List.of("1.21.9-1.21.11", "26.1-26.3");

    private MinecraftVersions() {
    }

    /**
     * Extracts the Minecraft version from a Bukkit version string.
     *
     * <p>Examples: {@code 1.21.9-R0.1-SNAPSHOT} becomes {@code 1.21.9};
     * {@code 26.1.2-51-abcdefg} becomes {@code 26.1.2}.</p>
     *
     * @param bukkitVersion the raw Bukkit version
     * @return the numeric version, or an empty string when it cannot be parsed
     */
    public static String extractMinecraftVersion(String bukkitVersion) {
        if (bukkitVersion == null || bukkitVersion.isEmpty()) {
            return "";
        }
        int end = bukkitVersion.length();
        for (int index = 0; index < bukkitVersion.length(); index++) {
            char character = bukkitVersion.charAt(index);
            boolean allowed = Character.isDigit(character) || character == '.';
            if (!allowed) {
                end = index;
                break;
            }
        }
        String candidate = bukkitVersion.substring(0, end);
        while (candidate.endsWith(".")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    /**
     * Parses a dotted version into its numeric parts.
     *
     * @param version the version string
     * @return the numeric parts, empty when nothing parseable was found
     */
    public static List<Integer> parts(String version) {
        List<Integer> result = new ArrayList<>(3);
        if (version == null) {
            return result;
        }
        for (String part : version.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // Non numeric segment (for example "-pre1"): stop here.
                break;
            }
        }
        return result;
    }

    /**
     * Compares two dotted version strings numerically.
     *
     * @param left  first version
     * @param right second version
     * @return negative, zero or positive following {@link Comparable} semantics
     */
    public static int compare(String left, String right) {
        List<Integer> leftParts = parts(left);
        List<Integer> rightParts = parts(right);
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < length; index++) {
            int leftPart = index < leftParts.size() ? leftParts.get(index) : 0;
            int rightPart = index < rightParts.size() ? rightParts.get(index) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    /**
     * Checks whether a version falls inside an inclusive {@code min-max} range.
     *
     * @param version the version to test
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @return true when the version is inside the range
     */
    public static boolean isWithin(String version, String minimum, String maximum) {
        return compare(version, minimum) >= 0 && compare(version, maximum) <= 0;
    }

    /**
     * Reports whether a server version is inside one of the ranges this build was
     * compiled against. This is advisory only and never blocks startup.
     *
     * @param version the detected Minecraft version
     * @return true when the version is a documented, supported version
     */
    public static boolean isSupported(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        for (String range : TESTED_RANGES) {
            String[] bounds = range.split("-");
            if (bounds.length == 2 && isWithin(version, bounds[0], bounds[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the supported ranges as documentation strings
     */
    public static List<String> testedRanges() {
        return TESTED_RANGES;
    }
}
