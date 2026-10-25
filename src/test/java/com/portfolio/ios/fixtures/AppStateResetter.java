package com.portfolio.ios.fixtures;

import com.portfolio.ios.core.Config;
import io.qameta.allure.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Puts the app back into a first-run state without reinstalling it.
 *
 * <p>The app gates onboarding on a single {@code @AppStorage} flag —
 * {@code hasCompletedOnboarding}, read in {@code IosNutritionApp.body} to choose
 * between {@code OnboardingView} and the main tab shell. Clearing that one key
 * is therefore enough to re-enter onboarding, and it costs a fraction of a
 * second versus an uninstall/reinstall cycle.
 *
 * <p>The app must be terminated first: {@code @AppStorage} is backed by
 * {@code UserDefaults}, and a running process keeps its own snapshot, so
 * writing the key underneath a live app has no effect on that process.
 *
 * <p>Driven through {@code xcrun simctl spawn} rather than a private API so it
 * works on any simulator without extra entitlements.
 */
public final class AppStateResetter {

    private static final String ONBOARDING_FLAG = "hasCompletedOnboarding";

    private AppStateResetter() {
    }

    /**
     * Clears {@code hasCompletedOnboarding} so the next launch starts at
     * step 0.
     *
     * @return true if the flag was written successfully
     */
    @Step("Reset app to first-run onboarding")
    public static boolean resetToFreshOnboarding() {
        if (Config.isReal()) {
            // fullReset reinstalls the .ipa on every session start, so the app
            // is already first-run; there is no simctl/UserDefaults lever here.
            return true;
        }
        terminate();
        if (Config.reinstallOnReset()) {
            return reinstall();
        }
        return writeBool(ONBOARDING_FLAG, false);
    }

    /**
     * Deletes everything the app has stored on the simulator, so the next
     * launch is a true first run.
     *
     * <p>{@link #resetToFreshOnboarding()} only clears the onboarding flag; the
     * SwiftData store survives it, so food, weight and water logged by earlier
     * runs reappear after the next onboarding (observed: Home showed a second
     * weight entry on the simulator but not on a fresh device install). Tests
     * that assert on Home's contents need this wipe instead.
     *
     * <p>Removes the SwiftData store from the App Group container — where the
     * app keeps it, not in its own data container — and the app's UserDefaults
     * domain. The app must be terminated first so nothing holds the store open.
     * On a real device this is a no-op: {@code fullReset} reinstalls the app.
     *
     * @return true if the store was removed (or already absent)
     */
    @Step("Wipe all app data (simulator)")
    public static boolean wipeAppData() {
        if (Config.isReal()) {
            return true;
        }
        terminate();
        String group = simctl("get_app_container", Config.udid(), Config.bundleId(),
                Config.appGroupId());
        boolean storeCleared = true;
        if (group != null && !group.isBlank()) {
            java.nio.file.Path support = java.nio.file.Paths.get(group, "Library", "Application Support");
            for (String file : List.of("default.store", "default.store-wal", "default.store-shm")) {
                try {
                    java.nio.file.Files.deleteIfExists(support.resolve(file));
                } catch (IOException e) {
                    System.err.println("[iosautomation] could not delete " + support.resolve(file) + ": " + e);
                    storeCleared = false;
                }
            }
        }
        // Clear the app's UserDefaults by the plist's path inside its data
        // container. Deleting the domain by bundle id does not reach the app's
        // sandboxed preferences (verified: hasCompletedOnboarding survived it and
        // the app relaunched straight onto Home). Exits non-zero when already
        // empty, which is fine.
        String data = simctl("get_app_container", Config.udid(), Config.bundleId(), "data");
        if (data != null && !data.isBlank()) {
            simulatorDefaults("delete", java.nio.file.Paths.get(data, "Library", "Preferences",
                    Config.bundleId()).toString());
        }
        // Belt and braces: the flag write below is the reset the onboarding
        // tests have always relied on, so the next launch shows onboarding
        // even if the delete above ever stops reaching the app.
        writeBool(ONBOARDING_FLAG, false);
        return storeCleared;
    }

    /**
     * Moves the app's install date back {@code days} days, so past days exist
     * on Home (simulator only).
     *
     * <p>Home's selectable range starts at {@code UserProfile.createdAt}, which
     * onboarding sets to "now" and nothing ever rewrites, so a freshly
     * onboarded app has no past day at all. SwiftData keeps it in SQLite as
     * {@code ZUSERPROFILE.ZCREATEDAT} (seconds since 2001-01-01), in the store
     * inside the App Group container. The app is terminated first so nothing
     * holds the store; the caller relaunches it. No device equivalent exists —
     * the store cannot be reached on a real iPhone.
     *
     * @return true if exactly the profile's date was moved back
     */
    @Step("Move the install date back {days} days (simulator)")
    public static boolean backdateInstall(int days) {
        if (Config.isReal()) {
            return false;
        }
        terminate();
        String group = simctl("get_app_container", Config.udid(), Config.bundleId(),
                Config.appGroupId());
        if (group == null || group.isBlank()) {
            return false;
        }
        String store = java.nio.file.Paths.get(group, "Library", "Application Support", "default.store")
                .toString();
        String result = run(List.of("/usr/bin/sqlite3", store,
                "UPDATE ZUSERPROFILE SET ZCREATEDAT = ZCREATEDAT - " + (days * 86400L) + ";"
                        + " SELECT changes();"));
        return "1".equals(result);
    }

    /**
     * Moves the running fast, and the plan that produced it, {@code hours}
     * into the past — as if it had started that much earlier. Simulator only;
     * the app is terminated first and must be relaunched after.
     *
     * <p>Everything the scheduler compares is shifted together — the fast's
     * start and planned end, the plan's start time of day, its one-off date,
     * creation stamp and backfill watermark — so on relaunch the app sees a
     * consistent history rather than a fast that no longer matches its plan.
     *
     * @return true if exactly one running fast was moved
     */
    public static boolean shiftActiveFastBack(int hours) {
        if (Config.isReal()) {
            return false;
        }
        terminate();
        String group = simctl("get_app_container", Config.udid(), Config.bundleId(),
                Config.appGroupId());
        if (group == null || group.isBlank()) {
            return false;
        }
        String store = java.nio.file.Paths.get(group, "Library", "Application Support", "default.store")
                .toString();
        String started = run(List.of("/usr/bin/sqlite3", store,
                "SELECT ZSTARTEDAT FROM ZFASTINGSESSION WHERE ZSTATUSRAW = 'active';"));
        if (started == null || started.isBlank() || started.contains("\n")) {
            return false;
        }
        long seconds = hours * 3600L;
        // Core Data stores seconds since 2001-01-01 UTC.
        java.time.LocalTime newStart = java.time.Instant.ofEpochSecond(978_307_200L
                        + (long) Double.parseDouble(started.trim()) - seconds)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        String result = run(List.of("/usr/bin/sqlite3", store,
                "UPDATE ZFASTINGSESSION SET ZSTARTEDAT = ZSTARTEDAT - " + seconds
                        + ", ZPLANNEDENDAT = ZPLANNEDENDAT - " + seconds + " WHERE ZSTATUSRAW = 'active';"
                        + " SELECT changes();"
                        + " UPDATE ZFASTINGSCHEDULE SET ZSTARTHOUR = " + newStart.getHour()
                        + ", ZSTARTMINUTE = " + newStart.getMinute()
                        + ", ZCREATEDAT = ZCREATEDAT - " + seconds
                        + ", ZONCEDATE = ZONCEDATE - " + seconds
                        + ", ZLASTMATERIALIZEDUPTO = ZLASTMATERIALIZEDUPTO - " + seconds + ";"));
        return result != null && result.startsWith("1");
    }

    /**
     * Moves the most recent food entry named {@code name} {@code days} days
     * into the past, as if it had been logged then. Simulator only; the app
     * is terminated first and must be relaunched after.
     *
     * @return true if exactly one entry was moved
     */
    public static boolean shiftLatestFoodEntryBack(String name, int days) {
        if (Config.isReal()) {
            return false;
        }
        terminate();
        String group = simctl("get_app_container", Config.udid(), Config.bundleId(),
                Config.appGroupId());
        if (group == null || group.isBlank()) {
            return false;
        }
        String store = java.nio.file.Paths.get(group, "Library", "Application Support", "default.store")
                .toString();
        String quoted = "'" + name.replace("'", "''") + "'";
        String result = run(List.of("/usr/bin/sqlite3", store,
                "UPDATE ZFOODENTRY SET ZLOGGEDAT = ZLOGGEDAT - " + (days * 86400L)
                        + " WHERE Z_PK = (SELECT MAX(Z_PK) FROM ZFOODENTRY WHERE ZNAME = " + quoted + ");"
                        + " SELECT changes();"));
        return "1".equals(result);
    }

    /** Runs a host command; trimmed output, or null on failure. Never throws. */
    private static String run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                System.err.println("[iosautomation] " + command.get(0) + " failed (" + process.exitValue() + "): "
                        + output.trim());
                return null;
            }
            return output.trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("[iosautomation] " + command.get(0) + " threw: " + e);
            return null;
        }
    }

    /**
     * Marks onboarding complete — the state a test needs when it wants to start
     * from the main tab shell rather than step 0.
     */
    @Step("Mark onboarding as complete")
    public static boolean markOnboardingComplete() {
        terminate();
        return writeBool(ONBOARDING_FLAG, true);
    }

    /**
     * Reads the flag straight out of the simulator's defaults database.
     *
     * <p><b>Eventual consistency warning.</b> The app commits
     * {@code hasCompletedOnboarding} and then transitions to the tab shell.
     * {@code UserDefaults} is flushed to disk asynchronously, so a read issued
     * in the same second as the transition can still return the old value
     * ({@code false}/{@code null}) even though the UI is already home. Callers
     * that check "did we really finish?" must use
     * {@link #waitForOnboardingComplete} instead of a single read.
     *
     * @return the stored value, or {@code null} if unset — which is the
     *         correct answer for a genuinely fresh install, so callers must
     *         treat {@code null} as "not completed", not as an error.
     */
    public static Boolean isOnboardingComplete() {
        if (Config.isReal()) {
            // UserDefaults lives inside the device sandbox; unreadable from here.
            // Callers must verify completion through the UI (main tab shell).
            return null;
        }
        String out = simulatorDefaults("read", Config.bundleId(), ONBOARDING_FLAG);
        if (out == null || out.isBlank()) {
            return null;
        }
        String value = out.trim();
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Stops the app if running. Safe to call when it is not. */
    public static void terminate() {
        if (Config.isReal()) {
            return;
        }
        simctlQuietly("terminate", Config.udid(), Config.bundleId());
    }

    /**
     * Polls {@link #isOnboardingComplete()} until it reads {@code true}.
     *
     * <p>Needed because the flag is flushed to disk asynchronously after the
     * app transitions to the tab shell — a single read right after
     * "Start My Journey" can still return {@code false}/{@code null} even
     * though onboarding genuinely finished. Never throws; returns the last
     * observed value so the caller can decide how to treat a slow flush.
     *
     * @param timeout how long to keep polling
     * @return {@code true} if the flag became {@code true} in time, otherwise
     *         the last value seen ({@code false} or {@code null})
     */
    @Step("Wait for hasCompletedOnboarding=true")
    public static Boolean waitForOnboardingComplete(java.time.Duration timeout) {
        if (Config.isReal()) {
            return null;
        }
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Boolean last = null;
        do {
            last = isOnboardingComplete();
            if (Boolean.TRUE.equals(last)) {
                return Boolean.TRUE;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        } while (System.nanoTime() < deadlineNanos);
        return last;
    }

    /**
     * Puts the app's privacy permissions — camera, microphone, speech
     * recognition, photos — back to "not asked yet", so the next use prompts
     * again ({@code simctl privacy reset all}). iOS terminates the app if it
     * is running. Notifications and Apple Health are not privacy services
     * and are not affected. Simulator only; returns false on a real device.
     */
    @Step("Reset the app's privacy permissions to not asked")
    public static boolean resetPrivacyPermissions() {
        if (Config.isReal()) {
            return false;
        }
        return simctl("privacy", Config.udid(), "reset", "all", Config.bundleId()) != null;
    }

    /**
     * Reinstalls the app that is installed now — the same build, copied out
     * of the simulator first — which is the only way to make iOS forget every
     * permission answer, notifications and Apple Health included. All app
     * data goes with it; callers restore the onboarded baseline afterwards.
     * Simulator only; returns false on a real device.
     */
    @Step("Reinstall the installed app (forgets every permission answer)")
    public static boolean reinstallInstalledApp() {
        if (Config.isReal()) {
            return false;
        }
        String installed = simctl("get_app_container", Config.udid(), Config.bundleId(), "app");
        if (installed == null || installed.isBlank()) {
            return false;
        }
        try {
            java.nio.file.Path copy = java.nio.file.Files.createTempDirectory("iosautomation-app")
                    .resolve(java.nio.file.Paths.get(installed).getFileName());
            // ditto keeps the bundle's signature, symlinks and attributes intact.
            if (run(List.of("ditto", installed, copy.toString())) == null) {
                return false;
            }
            terminate();
            simctlQuietly("uninstall", Config.udid(), Config.bundleId());
            return simctl("install", Config.udid(), copy.toString()) != null;
        } catch (java.io.IOException e) {
            System.err.println("[iosautomation] could not copy the installed app: " + e);
            return false;
        }
    }

    /**
     * Restores HealthKit authorization to "not determined" so the next
     * authorization request shows the system sheet again.
     *
     * <p>Why this exists: answering the HealthKit prompt — either way — is
     * recorded in the simulator's Health store, which survives app resets,
     * reinstalls, and UserDefaults rewrites. After the first answer, step 22's
     * request resolves immediately with no sheet, so a test that wants to
     * exercise the sheet itself (rather than just the advance) needs the store
     * wiped first.
     *
     * <p>Why it is opt-in and not part of the per-test reset: it shuts the
     * simulator down, deletes the Health store, and boots again — tens of
     * seconds and a brief window where every other session on that device
     * breaks. Call it from a {@code @BeforeClass} (or by hand) when the sheet
     * path itself is under test, never per method.
     *
     * <p>Safe only because the simulator is shut down first: healthd holds the
     * store open while booted, and deleting its files underneath it would
     * corrupt rather than reset.
     *
     * @return true if the simulator came back booted with a fresh Health store
     */
    @Step("Reset HealthKit authorization to not-determined")
    public static boolean resetHealthKitAuthorization() {
        if (Config.isReal()) {
            // The Health store on hardware cannot be wiped. First run shows the
            // sheet; later runs resolve with the recorded decision and the flow
            // observes the auto-advance either way — sheet interaction coverage
            // on device is first-run-only by platform design.
            System.err.println("[iosautomation] real device: HealthKit store cannot be reset; "
                    + "continuing — the Apple Health step handles already-resolved auth");
            return true;
        }
        terminate();
        simctlQuietly("shutdown", Config.udid());
        if (!deleteHealthStore()) {
            return false;
        }
        if (simctl("boot", Config.udid()) == null) {
            return false;
        }
        return simctl("bootstatus", Config.udid(), "-b") != null;
    }

    /**
     * Deletes the simulator's Health store directory contents.
     *
     * <p>Callers must shut the simulator down first. Returns false — rather
     * than half-deleting — if anything goes wrong, so a partial wipe can never
     * masquerade as a clean reset.
     */
    private static boolean deleteHealthStore() {
        try {
            java.nio.file.Path health = Config.simulatorDataDir().resolve("Library/Health");
            if (!java.nio.file.Files.isDirectory(health)) {
                System.err.println("[iosautomation] no Health store at " + health + "; nothing to reset");
                return true;
            }
            try (var entries = java.nio.file.Files.list(health)) {
                for (java.nio.file.Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
            return true;
        } catch (java.io.IOException e) {
            System.err.println("[iosautomation] could not delete Health store: " + e);
            return false;
        }
    }

    private static void deleteRecursively(java.nio.file.Path path) throws java.io.IOException {
        if (java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = java.nio.file.Files.list(path)) {
                for (java.nio.file.Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        java.nio.file.Files.deleteIfExists(path);
    }

    /**
     * Full reinstall from {@link Config#appPath()}. Only used when
     * {@code -Diosautomation.resetStrategy=reinstall} is set; it is the slower,
     * bulletproof option that also clears the SwiftData store.
     */
    private static boolean reinstall() {
        String app = Config.appPath();
        if (app.isBlank()) {
            throw new IllegalStateException(
                    "resetStrategy=reinstall requires -Diosautomation.appPath=<path to .app>");
        }
        simctlQuietly("uninstall", Config.udid(), Config.bundleId());
        String result = simctl("install", Config.udid(), app);
        return result != null;    }

    private static boolean writeBool(String key, boolean value) {
        return simulatorDefaults("write", Config.bundleId(), key, "-bool",
                Boolean.toString(value)) != null;
    }

    // ---------------------------------------------------------------- shell plumbing

    /**
     * Runs {@code xcrun simctl <args>}, returning trimmed output, or {@code null}
     * if the command failed.
     *
     * <p>The argument list is passed through verbatim and the caller supplies the
     * device UDID. An earlier version of this class injected the UDID
     * automatically after the first argument, which silently produced
     * {@code xcrun simctl defaults <UDID> <bundle> <key>} — a form simctl does
     * not accept, since {@code defaults} is not a simctl verb at all. The
     * correct spelling is {@code xcrun simctl spawn <UDID> defaults ...}. Making
     * every call site spell its own form removes that whole class of bug.
     *
     * <p>Never throws: a reset that cannot be applied should surface as a clear
     * test failure at the first step, not as an opaque stack trace from a
     * fixture.
     */
    static String simctl(String... args) {
        List<String> command = new ArrayList<>(List.of("xcrun", "simctl"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                System.err.println("[iosautomation] simctl " + String.join(" ", args)
                        + " failed (" + process.exitValue() + "): " + output.trim());
                return null;
            }
            return output.trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("[iosautomation] simctl " + String.join(" ", args) + " threw: " + e);
            return null;
        }
    }

    /**
     * Runs {@code defaults} inside the simulator's runtime via {@code spawn}.
     *
     * <p>This is the one form that needs the explicit {@code spawn} verb, so it
     * is kept separate from the plain simctl calls above.
     */
    static String simulatorDefaults(String... args) {
        List<String> command = new ArrayList<>(List.of("xcrun", "simctl", "spawn", Config.udid()));
        command.addAll(List.of("defaults"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                System.err.println("[iosautomation] defaults " + String.join(" ", args)
                        + " failed (" + process.exitValue() + "): " + output.trim());
                return null;
            }
            return output.trim();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("[iosautomation] defaults " + String.join(" ", args) + " threw: " + e);
            return null;
        }
    }

    /** Fire-and-forget variant for subcommands that are expected to no-op. */
    private static void simctlQuietly(String verb, String... args) {
        List<String> command = new ArrayList<>(List.of("xcrun", "simctl", verb));
        command.addAll(List.of(args));
        try {
            new ProcessBuilder(command).redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Best effort — e.g. terminating an app that is not running.
        }
    }
}