package com.portfolio.ios.core;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Builds and owns the IOSDriver for a test.
 *
 * <p>One session is created lazily per test and torn down in
 * {@link #quit()}. Nothing else in the suite is allowed to call
 * {@code new IOSDriver(...)}.
 *
 * <p>Which phone gets driven — Simulator or Real iPhone — comes from
 * {@code executionMode} in {@code config.properties}.
 */
public final class DriverFactory {

    private IOSDriver driver;

    /**
     * Real device only: true reinstalls the .ipa when the session starts
     * (a first-run app); false keeps the installed app and its data, and
     * Appium installs the .ipa only if that build is not on the phone yet.
     */
    private final boolean freshInstall;

    /** A session that reinstalls the app on a real device (the default). */
    public DriverFactory() {
        this(true);
    }

    public DriverFactory(boolean freshInstall) {
        this.freshInstall = freshInstall;
    }

    /**
     * Lazily creates the session.
     * Returns the existing driver on subsequent calls.
     */
    public IOSDriver driver() {
        if (driver == null) {
            driver = create();
        }
        return driver;
    }

    private IOSDriver create() {
        XCUITestOptions options = buildOptions(freshInstall);

        logStartup();

        IOSDriver created;
        try {
            created = new IOSDriver(url(), options);
        } catch (SessionNotCreatedException e) {
            if (isNotAuthorized(e) && Config.isReal() && stopLeftoverRunners(Config.udid())) {
                // A WebDriverAgent runner left attached by an earlier session (Appium keeps them
                // alive between sessions) loses its UI-testing authorization once the phone has
                // locked; reusing it fails with code 41 although the phone is unlocked now (seen
                // twice on the 11 Pro Max). A fresh runner asks again — so retry once.
                System.err.println("[iosautomation] the iPhone refused UI automation with a leftover WebDriverAgent "
                        + "attached; stopped it, retrying the session once");
                try {
                    created = new IOSDriver(url(), options);
                    created.manage().timeouts().implicitlyWait(Duration.ofSeconds(Config.implicitTimeoutSeconds()));
                    return created;
                } catch (SessionNotCreatedException again) {
                    e = again;
                }
            }
            if (isNotAuthorized(e)) {
                // XCTDaemonErrorDomain 41: iOS refuses UI automation on a locked phone, or after its
                // "enable automation mode" passcode prompt went unanswered (seen on iOS 27.2 with the
                // toggle on; the runner's xcresult says "Timed out while enabling automation mode").
                throw new SessionNotCreatedException("The iPhone refused UI automation (XCTDaemonErrorDomain 41). "
                        + "Unlock it and keep it unlocked for the run (Settings > Display & Brightness > "
                        + "Auto-Lock: Never), and check Settings > Developer > Enable UI Automation is on. "
                        + "If it is on, start a run while holding the phone: iOS can ask for the passcode to "
                        + "enable automation, and an unanswered prompt makes every later session fail like this.", e);
            }
            if (!isStaleWda(e)) {
                throw e;
            }
            // A WebDriverAgent left behind by an earlier session can still have
            // its xcodebuild process alive while its server is gone. Appium tries
            // to reuse it, gets ECONNREFUSED, and cleans it up — so one retry
            // starts a fresh WDA (observed twice on the simulator; the next
            // attempt always succeeded).
            System.err.println("[iosautomation] WebDriverAgent was unreachable (stale instance); "
                    + "retrying session once");
            created = new IOSDriver(url(), options);
        }

        // Implicit waits are intentionally kept low.
        // Framework waits are handled explicitly through Waits.
        created.manage().timeouts().implicitlyWait(
                Duration.ofSeconds(Config.implicitTimeoutSeconds())
        );

        return created;
    }

    /**
     * Builds XCUITest capabilities for the active execution mode.
     */
    static XCUITestOptions buildOptions() {
        return buildOptions(true);
    }

    static XCUITestOptions buildOptions(boolean freshInstall) {

        XCUITestOptions options = new XCUITestOptions();

        options.setAutomationName(
                ConfigReader.getOrDefault("automationName", "XCUITest")
        );

        options.setPlatformName(
                ConfigReader.getOrDefault("platform", "iOS")
        );

        options.setDeviceName(Config.deviceName());
        options.setPlatformVersion(Config.platformVersion());
        options.setUdid(Config.udid());

        options.setLanguage(Config.language());
        options.setLocale(Config.locale());

        options.setWdaLocalPort(Config.wdaLocalPort());

        if (Config.isReal()) {

            applyRealDeviceCapabilities(options, freshInstall);

        } else {

            // Simulator configuration
            options.setNoReset(false);

            if (Config.appPath().isBlank()) {

                options.setBundleId(Config.bundleId());

            } else {

                options.setApp(Config.appPath());
            }
        }

        options.setShouldTerminateApp(false);

        /*
         * Reduce animation-related flakiness.
         */
        options.setReduceMotion(true);

        /*
         * Alert handling stays disabled intentionally.
         * HealthKit and other permission prompts are part of onboarding.
         */
        options.setAutoAcceptAlerts(false);
        options.setAutoDismissAlerts(false);

        options.setNewCommandTimeout(
                Duration.ofSeconds(Config.newCommandTimeout())
        );

        /*
         * The app's own test switch (AppReviewPrompter.swift:42). Without it,
         * development builds show StoreKit's "Enjoying Cal Care?" dialog after
         * every food log, covering Home (observed on the simulator).
         */
        options.setProcessArguments(
                new io.appium.java_client.ios.options.wda.ProcessArguments(
                        List.of("-disableReviewPrompt"))
        );

        options.setForceAppLaunch(true);

        return options;
    }

    /**
     * Real-device capabilities.
     *
     * The signed .ipa is installed fresh for each session.
     * WebDriverAgent is also signed using the configured Apple Team.
     */
    private static void applyRealDeviceCapabilities(
            XCUITestOptions options, boolean freshInstall) {

        /*
         * Validate .ipa path.
         */
        if (Config.realAppPath().isBlank()) {

            throw new IllegalStateException(
                    "executionMode=real needs real.appPath=<path to signed .ipa> "
                            + "in config.properties"
            );
        }

        /*
         * Validate Apple Team ID.
         */
        if (Config.xcodeOrgId().isBlank()) {

            throw new IllegalStateException(
                    "executionMode=real needs real.xcodeOrgId=<Apple Team ID> "
                            + "in config.properties so Appium can sign "
                            + "WebDriverAgent"
            );
        }

        /*
         * iOS Nutrition App IPA.
         */
        options.setApp(Config.realAppPath());

        /*
         * Fresh install (first run), or keep the installed app and its data
         * (regression classes restore their own baseline; see DeviceBaseline).
         */
        options.setFullReset(freshInstall);
        options.setNoReset(!freshInstall);

        /*
         * WebDriverAgent signing.
         */
        options.setXcodeCertificate(
                new io.appium.java_client.ios.options.wda.XcodeCertificate(
                        Config.xcodeOrgId(),
                        Config.xcodeSigningId()
                )
        );

        /*
         * Optional custom WDA bundle ID.
         */
        if (!Config.updatedWdaBundleId().isBlank()) {

            options.setUpdatedWdaBundleId(
                    Config.updatedWdaBundleId()
            );
        }

        /*
         * Pin the WDA re-signing identity by SHA-1.
         *
         * WDA's "Embed app icon into Runner.app" post-build step re-signs the
         * runner using $EXPANDED_CODE_SIGN_IDENTITY, falling back to the
         * certificate *name*. With several same-named certificates in the
         * keychain that name is ambiguous, codesign fails, and xcodebuild
         * exits 65 (MIInstallerErrorDomain 13 if the half-signed runner is
         * ever installed). Settings in an -xcconfig are exported to that step.
         */
        if (!Config.wdaSigningCertSha1().isBlank()) {

            options.setCapability(
                    "xcodeConfigFile",
                    wdaSigningXcconfig(Config.wdaSigningCertSha1()).toString()
            );
        }

        /*
         * Build the device WDA in its own DerivedData so an open Xcode window
         * or the simulator WDA build never shares (or corrupts) its products.
         */
        if (!Config.wdaDerivedDataPath().isBlank()) {

            options.setDerivedDataPath(
                    Config.wdaDerivedDataPath()
            );
        }

        /*
         * IMPORTANT:
         * Show the complete Xcode/WDA build output.
         *
         * This will allow us to see the actual reason for
         * xcodebuild exit code 65.
         */
        options.setCapability(
                "showXcodeLog",
                true
        );

        /*
         * Keep WDA's DerivedData/Logs (xcodebuild logs and .xcresult).
         * clearSystemFiles deletes exactly that folder at session end, which
         * is the evidence needed when a device build or launch fails.
         */
        options.setCapability(
                "clearSystemFiles",
                false
        );

        /*
         * A phone WDA has not run on yet needs its provisioning profile updated.
         */
        if (Config.allowProvisioningDeviceRegistration()) {

            options.setCapability(
                    "allowProvisioningDeviceRegistration",
                    true
            );
        }

        /*
         * Use prebuilt WDA only when explicitly configured.
         */
        options.setUsePrebuiltWda(
                Config.usePrebuiltWda()
        );

        /*
         * First WDA build on a real device can take time.
         */
        options.setWdaLaunchTimeout(
                Duration.ofSeconds(180)
        );
    }

    private static boolean isNotAuthorized(SessionNotCreatedException e) {
        return String.valueOf(e.getMessage()).contains("Not authorized for performing UI testing actions");
    }

    /**
     * Stops the WebDriverAgent runners ({@code xcodebuild … -destination id=<udid>})
     * still attached to this phone; true if there were any.
     */
    private static boolean stopLeftoverRunners(String udid) {
        String pattern = "xcodebuild.*id=" + udid;
        try {
            if (pids(pattern).isEmpty()) {
                return false;
            }
            new ProcessBuilder("pkill", "-f", pattern).start().waitFor();
            Waits.untilTrue(Waits.SHORT, "the leftover WebDriverAgent runner to stop", () -> pids(pattern).isEmpty());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String pids(String pattern) {
        try {
            Process find = new ProcessBuilder("pgrep", "-f", pattern).start();
            String out = new String(find.getInputStream().readAllBytes()).trim();
            find.waitFor();
            return out;
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /** True when session creation failed because WDA's server could not be reached. */
    private static boolean isStaleWda(SessionNotCreatedException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("ECONNREFUSED")
                || message.contains("Could not proxy command to the remote server");
    }

    /**
     * Writes the xcconfig Appium passes to the WDA build as {@code -xcconfig}.
     *
     * <p>Generated from {@code real.wdaSigningCertSha1} so config.properties
     * stays the single place device signing is configured.
     */
    private static Path wdaSigningXcconfig(String sha1) {

        String hash = sha1.trim().toUpperCase();

        if (!hash.matches("[0-9A-F]{40}")) {

            throw new IllegalStateException(
                    "real.wdaSigningCertSha1 must be a 40-character SHA-1, was '"
                            + sha1 + "' — list candidates with: "
                            + "security find-identity -v -p codesigning"
            );
        }

        Path file = Config.artifactsDir().resolve("wda-signing.xcconfig");

        try {

            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    "// Generated by DriverFactory from real.wdaSigningCertSha1.\n"
                            + "EXPANDED_CODE_SIGN_IDENTITY = " + hash + "\n",
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Could not write WDA signing xcconfig to " + file, e
            );
        }

        return file;
    }

    /**
     * Logs the actual target before creating the session.
     */
    private void logStartup() {

        String mode = Config.isReal()
                ? "REAL DEVICE"
                : "SIMULATOR";

        System.out.println(
                "[iosautomation] ---------------- driver startup ----------------"
        );

        System.out.println(
                "[iosautomation] Execution Mode: " + mode
        );

        System.out.println(
                "[iosautomation] Device: " + Config.deviceName()
        );

        System.out.println(
                "[iosautomation] UDID: " + Config.udid()
        );

        System.out.println(
                "[iosautomation] Platform: "
                        + ConfigReader.getOrDefault("platform", "iOS")
                        + " "
                        + Config.platformVersion()
        );

        System.out.println(
                "[iosautomation] Automation: "
                        + ConfigReader.getOrDefault(
                        "automationName",
                        "XCUITest"
                )
        );

        System.out.println(
                "[iosautomation] WDA local port: " + Config.wdaLocalPort()
        );

        System.out.println(
                "[iosautomation] ------------------------------------------------"
        );
    }

    /**
     * Tears down the driver.
     */
    public void quit() {

        if (driver != null) {

            try {

                driver.quit();

            } catch (RuntimeException ignored) {

                // A wedged WDA must never mask the test failure.

            } finally {

                driver = null;
            }
        }
    }

    /**
     * Checks whether the current driver session is alive.
     */
    public boolean isAlive() {

        if (driver == null) {
            return false;
        }

        try {

            driver.getPageSource();

            return true;

        } catch (RuntimeException e) {

            return false;
        }
    }

    /**
     * Creates a WebDriverWait using the framework driver.
     */
    public WebDriverWait wait(Duration timeout) {

        return new WebDriverWait(
                driver(),
                timeout
        );
    }

    /**
     * Returns the raw WDA element ID.
     */
    public static String elementId(WebElement element) {

        return ((RemoteWebElement) element).getId();
    }

    /**
     * Returns an empty element list.
     */
    public static List<WebElement> emptyList() {

        return List.of();
    }

    /**
     * Appium server URL.
     */
    private static URL url() {

        try {

            return new URL(
                    Config.appiumUrl()
            );

        } catch (MalformedURLException e) {

            throw new IllegalStateException(
                    "Invalid appiumUrl="
                            + Config.appiumUrl()
                            + " — expected http://host:port",
                    e
            );
        }
    }
}