package com.portfolio.ios.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A device the suite can drive, described in its own file:
 * {@code src/test/resources/devices/<name>.properties}.
 *
 * <pre>
 * type=real                          # simulator | real
 * deviceName=iPhone 11 Pro Max Old   # as Xcode / Finder shows it
 * platformVersion=26.3.1             # iOS version
 * udid=00008030-000A28101122802E
 * wdaLocalPort=8103                  # optional: WebDriverAgent port for this device
 * </pre>
 *
 * <p>Which profile is used ({@link Config#deviceProfile()}):
 * {@code device} — {@code -Diosautomation.device=<name>}, {@code IOS_AUTOMATION_DEVICE}, or
 * {@code device=} in {@code config.properties} — picks one for this run, and
 * its {@code type} sets the execution mode; otherwise
 * {@code simulator.device} / {@code real.device} in {@code config.properties}
 * name the device for each mode. Adding a phone or simulator is a new file;
 * no Java changes.
 *
 * <p>The reserved name {@code auto} needs no file: it is the iPhone or iPad
 * plugged in by USB, whichever it is ({@link ConnectedDevice}).
 */
public final class DeviceProfile {

    /** The phone on the USB cable, detected at run time. */
    public static final String AUTO = "auto";

    private static final String DIR = "devices";
    private static final List<String> TYPES = List.of("simulator", "real");
    private static final Map<String, Properties> LOADED = new ConcurrentHashMap<>();

    private DeviceProfile() {
    }

    /** {@code simulator} or {@code real}. */
    public static String type(String name) {
        if (AUTO.equals(name)) {
            return "real";
        }
        String type = value(name, "type");
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalStateException("Device profile '" + name + "' (" + file(name)
                    + ") must set type=simulator or type=real, was '" + type + "'");
        }
        return type;
    }

    /** {@code key} from the profile, or null when the file does not set it. */
    public static String value(String name, String key) {
        if (AUTO.equals(name)) {
            ConnectedDevice.Device device = ConnectedDevice.detect();
            return switch (key) {
                case "deviceName" -> device.name();
                case "platformVersion" -> device.iosVersion();
                case "udid" -> device.udid();
                default -> null;
            };
        }
        String value = load(name).getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** {@code key} from the profile; fails naming the file when it is missing. */
    public static String require(String name, String key) {
        String value = value(name, key);
        if (value == null) {
            throw new IllegalStateException("Device profile '" + name + "' (" + file(name) + ") has no '" + key + "'");
        }
        return value;
    }

    private static Properties load(String name) {
        return LOADED.computeIfAbsent(name, DeviceProfile::read);
    }

    private static Properties read(String name) {
        try (InputStream in = DeviceProfile.class.getClassLoader().getResourceAsStream(DIR + "/" + name + ".properties")) {
            if (in == null) {
                throw new IllegalStateException("No device profile '" + name + "': expected " + file(name)
                        + ". Available: " + available() + ", or auto (the phone plugged in by USB)");
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read device profile " + file(name), e);
        }
    }

    /** Names of the profiles on the classpath, for error messages. */
    static List<String> available() {
        URL dir = DeviceProfile.class.getClassLoader().getResource(DIR);
        if (dir == null || !"file".equals(dir.getProtocol())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(Paths.get(dir.toURI()))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".properties"))
                    .map(f -> f.substring(0, f.length() - ".properties".length()))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException e) {
            return List.of();
        }
    }

    private static String file(String name) {
        return "src/test/resources/" + DIR + "/" + name + ".properties";
    }
}
