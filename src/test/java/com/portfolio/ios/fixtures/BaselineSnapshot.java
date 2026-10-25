package com.portfolio.ios.fixtures;

import com.portfolio.ios.core.Config;
import io.qameta.allure.Step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A saved copy of the app's data right after onboarding — the "baseline" —
 * so regression tests start from a freshly onboarded app in seconds instead
 * of walking the 27 onboarding steps again. Simulator only.
 *
 * <p>What the app keeps, read from its source: the onboarding gate is
 * {@code @AppStorage("hasCompletedOnboarding")} (UserDefaults,
 * {@code IosNutritionApp.swift:76}); finishing onboarding also writes the profile,
 * first weight entry, gamification profile and streak records to SwiftData
 * ({@code OnboardingView.swift}). So a baseline is both:
 * <ul>
 *   <li>the SwiftData store in the App Group container —
 *       {@code default.store}, {@code -wal}, {@code -shm} and the
 *       {@code .default_SUPPORT} folder of externally stored data;</li>
 *   <li>the app's UserDefaults, exported and imported through the
 *       simulator's own {@code defaults} tool so iOS's preferences cache
 *       stays consistent.</li>
 * </ul>
 * The app has no Keychain data. The app must be terminated before a save or
 * restore.
 *
 * <p>A baseline is keyed by the installed build — version, build number and a
 * hash of its code — and today's date: a new build may change the data model,
 * and date-based features read the install date from the profile, so either
 * change needs one fresh onboarding.
 */
public final class BaselineSnapshot {

    private static final List<String> STORE_FILES = List.of("default.store", "default.store-wal", "default.store-shm");
    private static final String BLOB_DIR = ".default_SUPPORT";
    private static final String DEFAULTS_FILE = "defaults.plist";
    private static final String COMPLETE_MARKER = "complete";

    private BaselineSnapshot() {
    }

    /** "1.0.13-2-2026-10-06": installed version, build and today's date. */
    public static String key() {
        String info = AppStateResetter.simctl("appinfo", Config.udid(), Config.bundleId());
        String version = field(info, "CFBundleShortVersionString");
        String build = field(info, "CFBundleVersion");
        return version + "-" + build + "-" + binaryFingerprint(field(info, "CFBundleExecutable")) + "-" + LocalDate.now();
    }

    private static String fingerprintFor;
    private static String fingerprint;

    /**
     * The installed build's code, hashed: CI rebuilds the app on every push
     * without changing its version or build number, and a baseline saved by
     * one binary must not be restored into another. A Debug build keeps its
     * code in {@code <name>.debug.dylib}, the executable being a stub, so both
     * are hashed. Cached until the installed files change.
     */
    private static synchronized String binaryFingerprint(String executable) {
        String app = AppStateResetter.simctl("get_app_container", Config.udid(), Config.bundleId(), "app");
        if (app == null || app.isBlank()) {
            throw new IllegalStateException("App bundle not found for " + Config.bundleId());
        }
        List<Path> binaries = Stream.of(executable, executable + ".debug.dylib")
                .map(name -> Paths.get(app, name)).filter(Files::exists).toList();
        StringBuilder identity = new StringBuilder(app);
        for (Path binary : binaries) {
            try {
                identity.append('|').append(Files.size(binary)).append('|').append(Files.getLastModifiedTime(binary));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + binary, e);
            }
        }
        if (!identity.toString().equals(fingerprintFor)) {
            try {
                java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
                for (Path binary : binaries) {
                    try (java.io.InputStream in = Files.newInputStream(binary)) {
                        byte[] buffer = new byte[1 << 16];
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            sha1.update(buffer, 0, n);
                        }
                    }
                }
                fingerprint = java.util.HexFormat.of().formatHex(sha1.digest()).substring(0, 10);
                fingerprintFor = identity.toString();
            } catch (IOException | java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("Cannot fingerprint the installed app", e);
            }
        }
        return fingerprint;
    }

    /** {@code <stateDir>/baseline} (see {@link Config#stateDir()}). */
    private static Path root() {
        return Config.stateDir().resolve("baseline");
    }

    public static Path dir() {
        return root().resolve(key());
    }

    /** True when a complete baseline exists for the installed build and today. */
    public static boolean exists() {
        return !Config.isReal() && Files.exists(dir().resolve(COMPLETE_MARKER));
    }

    /** Saves the current (terminated) app's data as the baseline; older baselines are removed. */
    @Step("Save the onboarded baseline")
    public static boolean save() {
        if (Config.isReal()) {
            return false;
        }
        AppStateResetter.terminate();
        Path target = dir();
        try {
            deleteOtherBaselines(target);
            deleteRecursively(target);
            Files.createDirectories(target.resolve("store"));
            Path support = appSupport();
            for (String file : STORE_FILES) {
                if (Files.exists(support.resolve(file))) {
                    Files.copy(support.resolve(file), target.resolve("store").resolve(file),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (Files.exists(support.resolve(BLOB_DIR))) {
                copyRecursively(support.resolve(BLOB_DIR), target.resolve("store").resolve(BLOB_DIR));
            }
            if (AppStateResetter.simulatorDefaults("export", preferencesDomain(),
                    target.resolve(DEFAULTS_FILE).toString()) == null) {
                return false;
            }
            Files.writeString(target.resolve(COMPLETE_MARKER), key());
            return true;
        } catch (IOException e) {
            System.err.println("[iosautomation] could not save the baseline: " + e);
            return false;
        }
    }

    /** Puts the baseline back into the (terminated) app. */
    @Step("Restore the onboarded baseline")
    public static boolean restore() {
        if (!exists()) {
            return false;
        }
        AppStateResetter.terminate();
        Path source = dir();
        try {
            Path support = appSupport();
            for (String file : STORE_FILES) {
                Files.deleteIfExists(support.resolve(file));
            }
            deleteRecursively(support.resolve(BLOB_DIR));
            for (String file : STORE_FILES) {
                if (Files.exists(source.resolve("store").resolve(file))) {
                    Files.copy(source.resolve("store").resolve(file), support.resolve(file),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (Files.exists(source.resolve("store").resolve(BLOB_DIR))) {
                copyRecursively(source.resolve("store").resolve(BLOB_DIR), support.resolve(BLOB_DIR));
            }
        } catch (IOException e) {
            System.err.println("[iosautomation] could not restore the baseline store: " + e);
            return false;
        }
        // Replace the app's preferences as a whole: clear, then import the saved set.
        AppStateResetter.simulatorDefaults("delete", preferencesDomain());
        return AppStateResetter.simulatorDefaults("import", preferencesDomain(),
                source.resolve(DEFAULTS_FILE).toString()) != null;
    }

    // ---------------------------------------------------------------- plumbing

    private static Path appSupport() throws IOException {
        String group = AppStateResetter.simctl("get_app_container", Config.udid(), Config.bundleId(),
                Config.appGroupId());
        if (group == null || group.isBlank()) {
            throw new IOException("App Group container not found for " + Config.appGroupId());
        }
        Path support = Paths.get(group, "Library", "Application Support");
        Files.createDirectories(support);
        return support;
    }

    /**
     * The app's preferences by file path inside its data container — the form
     * that reaches the sandboxed app's own UserDefaults (see
     * {@link AppStateResetter#wipeAppData()}).
     */
    private static String preferencesDomain() {
        String data = AppStateResetter.simctl("get_app_container", Config.udid(), Config.bundleId(), "data");
        if (data == null || data.isBlank()) {
            throw new IllegalStateException("App data container not found for " + Config.bundleId());
        }
        return Paths.get(data, "Library", "Preferences", Config.bundleId()).toString();
    }

    private static String field(String info, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*\"?([^\";\\n]+)\"?;").matcher(info == null ? "" : info);
        if (!m.find()) {
            throw new IllegalStateException("Could not read " + name + " of the installed app: " + info);
        }
        return m.group(1).trim();
    }

    private static void deleteOtherBaselines(Path keep) throws IOException {
        if (!Files.isDirectory(root())) {
            return;
        }
        try (Stream<Path> dirs = Files.list(root())) {
            for (Path other : dirs.toList()) {
                if (!other.equals(keep)) {
                    deleteRecursively(other);
                }
            }
        }
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                Path dest = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
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
