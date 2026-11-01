package dev.bluelabs.honeycomb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Version information baked into the jar by the build.
 *
 * <p>Each artifact knows which Minecraft range it was compiled for, so
 * {@code /honeycomb version} can state it without guessing from the server.</p>
 */
public final class BuildInfo {

    private static final String UNKNOWN = "unknown";
    private static final Properties PROPERTIES = load();

    private BuildInfo() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = BuildInfo.class.getResourceAsStream("/honeycomb-build.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // A missing build file is not fatal: the accessors fall back to "unknown".
        }
        return properties;
    }

    /**
     * @return the plugin version, for example {@code 1.0.0}
     */
    public static String version() {
        return PROPERTIES.getProperty("version", UNKNOWN);
    }

    /**
     * @return the Minecraft range this artifact was built for
     */
    public static String minecraftRange() {
        return PROPERTIES.getProperty("minecraft-range", UNKNOWN);
    }

    /**
     * @return the Java feature release this artifact targets
     */
    public static String javaTarget() {
        return PROPERTIES.getProperty("java-target", UNKNOWN);
    }
}
