package com.portfolio.ios.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized configuration reader: the single source of every
 * device-specific and environment-specific value in the suite.
 *
 * <p>Values resolve in this order (first non-blank wins):
 * <ol>
 *   <li>JVM system property — {@code -Diosautomation.executionMode=real}</li>
 *   <li>Environment variable — {@code IOS_AUTOMATION_EXECUTION_MODE=real}
 *       (dots become underscores, upper-cased)</li>
 *   <li>{@code config.properties} on the test classpath</li>
 * </ol>
 *
 * <p>No hardcoded device details live in Java. A device's name, UDID and iOS
 * version come from its profile ({@link DeviceProfile},
 * {@code src/test/resources/devices/}); machine settings — the .ipa, signing,
 * ports, folders — from the file above. Any single key can still be
 * overridden from the CLI for one run.
 */
public final class ConfigReader {

    private static final String FILE = "config.properties";
    private static final String SYSPROP_PREFIX = "iosautomation.";

    private static volatile Properties loaded;
    /**
     * While a test fixture is loaded, ambient {@code -D} / environment
     * overrides are ignored so capability-selection tests are hermetic: a
     * stray {@code -Diosautomation.executionMode=real} on the command line must not
     * flip assertions that pinned simulator content.
     */
    private static volatile boolean testLocked;

    private ConfigReader() {
    }

    /**
     * Reads {@code key} (e.g. {@code "bundleId"}, {@code "real.udid"}).
     *
     * @throws IllegalStateException when the key is missing everywhere —
     *         failing here names the exact property to add, instead of
     *         surfacing later as an opaque Appium rejection
     */
    public static String get(String key) {
        if (!testLocked) {
            String fromProp = System.getProperty(SYSPROP_PREFIX + key);
            if (isSet(fromProp)) {
                return fromProp.trim();
            }
            String fromEnv = System.getenv(envName(key));
            if (isSet(fromEnv)) {
                return fromEnv.trim();
            }
        }
        String fromFile = properties().getProperty(key);
        if (isSet(fromFile)) {
            return fromFile.trim();
        }
        throw new IllegalStateException(
                "Missing required configuration '" + key + "': set it in "
                        + FILE + " (src/test/resources/" + FILE + "), or pass "
                        + "-D" + SYSPROP_PREFIX + key + "=<value>");
    }

    /**
     * {@code key} only when set explicitly for this run — as a
     * {@code -Diosautomation.} property or an {@code IOS_AUTOMATION_} environment variable —
     * else null. Lets a device profile sit between explicit overrides and
     * {@code config.properties}.
     */
    public static String override(String key) {
        if (testLocked) {
            return null;
        }
        String fromProp = System.getProperty(SYSPROP_PREFIX + key);
        if (isSet(fromProp)) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(envName(key));
        return isSet(fromEnv) ? fromEnv.trim() : null;
    }

    /** Reads {@code key}, falling back to {@code def} when set nowhere. */
    public static String getOrDefault(String key, String def) {
        try {
            return get(key);
        } catch (IllegalStateException missing) {
            return def;
        }
    }

    public static int getInt(String key, int def) {
        String raw = getOrDefault(key, null);
        if (raw == null) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Configuration '" + key + "' must be an integer, was '" + raw + "'", e);
        }
    }

    public static boolean getBool(String key, boolean def) {
        String raw = getOrDefault(key, null);
        if (raw == null) {
            return def;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    /** Loads {@code config.properties} once; safe under parallel callers. */
    private static Properties properties() {
        Properties cached = loaded;
        if (cached != null) {
            return cached;
        }
        synchronized (ConfigReader.class) {
            if (loaded != null) {
                return loaded;
            }
            try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream(FILE)) {
                if (in == null) {
                    throw new IllegalStateException(
                            "Configuration file '" + FILE + "' not found on the test classpath "
                                    + "(expected at src/test/resources/" + FILE + ")");
                }
                Properties props = new Properties();
                props.load(in);
                loaded = props;
                return props;
            } catch (IOException e) {
                throw new IllegalStateException("Could not read configuration file '" + FILE + "'", e);
            }
        }
    }

    /**
     * Test-only seam: replaces the loaded file content so capability-selection
     * tests can assert both modes without editing the real file.
     */
    static void loadForTests(Properties props) {
        synchronized (ConfigReader.class) {
            loaded = props;
            testLocked = true;
        }
    }

    /** Test-only seam: drops the override so later tests re-read the file. */
    static void resetForTests() {
        synchronized (ConfigReader.class) {
            loaded = null;
            testLocked = false;
        }
    }

    private static boolean isSet(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String envName(String key) {
        return "IOS_AUTOMATION_" + key.replace('.', '_').toUpperCase();
    }
}
