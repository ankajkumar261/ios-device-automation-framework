package com.portfolio.ios.fixtures;

import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.appmanagement.ApplicationState;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The real-device counterpart of {@link BaselineSnapshot}: the app's
 * SwiftData store right after onboarding, saved from the iPhone and put back
 * before each class — about 1 s over USB instead of reinstalling the app and
 * onboarding again (about 3.5 min).
 *
 * <p>What the Mac can reach on the phone, verified with {@code devicectl}
 * (iOS 27, development-signed build): the App Group container — where the
 * app keeps its SwiftData store — can be read and written; the app's own
 * container, which holds its UserDefaults, cannot even be listed. So the
 * store is restored and preferences are not: tests that change a setting put
 * it back themselves, as they already do.
 *
 * <p>{@code devicectl device copy to} refuses to overwrite a file unless
 * {@code --remove-existing-content} is given, and that option empties the
 * destination's whole folder first. So a restore copies {@code default.store}
 * with it — clearing {@code Library/Application Support} — and everything
 * else without it, onto paths that no longer exist.
 *
 * <p>Keyed by the .ipa (size and date) and today's date, like the simulator
 * baseline: a new build or a new day onboards once more.
 */
public final class DeviceBaseline {

    private static final String SUPPORT = "Library/Application Support";
    private static final List<String> STORE_FILES = List.of("default.store", "default.store-wal", "default.store-shm");
    /** Widget artwork the app downloads; kept so a restore does not make it download again. */
    private static final String WIDGET_IMAGES = "WidgetImages";
    private static final String COMPLETE_MARKER = "complete";

    private DeviceBaseline() {
    }

    /** "&lt;ipa size&gt;-&lt;ipa modified&gt;-2026-10-06" — which build, and which day. */
    public static String key() {
        try {
            Path ipa = Paths.get(Config.realAppPath());
            return Files.size(ipa) + "-" + Files.getLastModifiedTime(ipa).toMillis() + "-" + LocalDate.now();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read real.appPath " + Config.realAppPath(), e);
        }
    }

    /** {@code <stateDir>/device-baseline} (see {@link Config#stateDir()}). */
    private static Path root() {
        return Config.stateDir().resolve("device-baseline");
    }

    public static Path dir() {
        return root().resolve(Config.udid()).resolve(key());
    }

    /** True when a complete baseline exists for this .ipa, this phone and today. */
    public static boolean exists() {
        return Config.isReal() && Files.exists(dir().resolve(COMPLETE_MARKER));
    }

    /** Forgets this phone's baselines, e.g. when the app was reinstalled since one was saved. */
    public static void discard() {
        try {
            deleteRecursively(root().resolve(Config.udid()));
        } catch (IOException e) {
            System.err.println("[iosautomation] could not delete the device baseline: " + e);
        }
    }

    /** Saves the (onboarded) app's store from the phone; older baselines of this phone are removed. */
    @Step("Save the onboarded baseline from the iPhone")
    public static boolean save(IOSDriver driver) {
        terminate(driver);
        Path target = dir();
        try {
            deleteRecursively(root().resolve(Config.udid()));
            Files.createDirectories(target);
            for (String file : STORE_FILES) {
                if (!devicectl("copy", "from", "--source", SUPPORT + "/" + file,
                        "--destination", target.resolve(file).toString())) {
                    return false;
                }
            }
            // Best effort: absent until the app has downloaded its widget artwork.
            devicectl("copy", "from", "--source", SUPPORT + "/" + WIDGET_IMAGES,
                    "--destination", target.resolve(WIDGET_IMAGES).toString());
            Files.writeString(target.resolve(COMPLETE_MARKER), key());
            return true;
        } catch (IOException e) {
            System.err.println("[iosautomation] could not save the device baseline: " + e);
            return false;
        }
    }

    /** Puts the baseline store back on the phone; the app is terminated first and left terminated. */
    @Step("Restore the onboarded baseline on the iPhone")
    public static boolean restore(IOSDriver driver) {
        if (!exists()) {
            return false;
        }
        terminate(driver);
        Path source = dir();
        // First file with the flag: clears Application Support, then writes it.
        if (!devicectl("copy", "to", "--source", source.resolve(STORE_FILES.get(0)).toString(),
                "--destination", SUPPORT + "/" + STORE_FILES.get(0), "--remove-existing-content", "true")) {
            return false;
        }
        for (String file : STORE_FILES.subList(1, STORE_FILES.size())) {
            if (!devicectl("copy", "to", "--source", source.resolve(file).toString(),
                    "--destination", SUPPORT + "/" + file)) {
                return false;
            }
        }
        if (Files.isDirectory(source.resolve(WIDGET_IMAGES))) {
            devicectl("copy", "to", "--source", source.resolve(WIDGET_IMAGES).toString(),
                    "--destination", SUPPORT + "/" + WIDGET_IMAGES);
        }
        return true;
    }

    // ---------------------------------------------------------------- plumbing

    /** Through WebDriverAgent: devicectl's own terminate needs --kill and the pid. */
    private static void terminate(IOSDriver driver) {
        driver.executeScript("mobile: terminateApp", Map.of("bundleId", Config.bundleId()));
        Waits.untilTrue(Waits.SHORT, "the app to stop",
                () -> driver.queryAppState(Config.bundleId()) == ApplicationState.NOT_RUNNING);
    }

    /**
     * {@code xcrun devicectl device <args>} against the App Group container,
     * retried twice: over a cable it is reliable, but a dropped connection
     * shows up as a one-off error.
     */
    private static boolean devicectl(String... args) {
        List<String> command = new ArrayList<>(List.of("xcrun", "devicectl", "device"));
        command.addAll(List.of(args));
        command.addAll(List.of("--device", Config.udid(), "--domain-type", "appGroupDataContainer",
                "--domain-identifier", Config.appGroupId()));
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes());
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                } else if (process.exitValue() == 0 && !output.contains("ERROR:")) {
                    return true;
                }
                System.err.println("[iosautomation] devicectl " + String.join(" ", args) + " failed (attempt "
                        + attempt + "): " + output.strip());
            } catch (IOException e) {
                System.err.println("[iosautomation] devicectl could not run: " + e);
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
