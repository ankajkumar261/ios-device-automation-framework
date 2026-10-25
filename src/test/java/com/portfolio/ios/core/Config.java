package com.portfolio.ios.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed facade over {@link ConfigReader}.
 *
 * <p>Kept so the rest of the framework (driver, fixtures, page objects, tests)
 * keeps calling {@code Config.udid()} etc. without caring where values come
 * from. Device details come from a device profile ({@link DeviceProfile}) or
 * the {@code simulator.*} / {@code real.*} keys; machine and environment
 * settings from {@code config.properties}. This class holds <b>no</b> device
 * details, only the key names and how they resolve.
 */
public final class Config {

    private Config() {
    }

    // ---------------------------------------------------------------- mode

    /**
     * {@code simulator} or {@code real}, from {@code executionMode}.
     * Anything else fails fast — a typo here must not silently drive the wrong
     * phone.
     */
    public static String executionMode() {
        String device = ConfigReader.getOrDefault("device", null);
        if (device != null) {
            // A device chosen for the run decides the mode; an explicit mode that
            // disagrees is a mistake worth stopping for, not a silent override.
            String type = DeviceProfile.type(device);
            String explicit = ConfigReader.override("executionMode");
            if (explicit != null && !explicit.equals(type)) {
                throw new IllegalStateException("device=" + device + " is a " + type
                        + " device, but executionMode=" + explicit + " was also given");
            }
            return type;
        }
        String mode = ConfigReader.getOrDefault("executionMode", "simulator").trim();
        if (!mode.equals("simulator") && !mode.equals("real")) {
            throw new IllegalStateException(
                    "Configuration 'executionMode' must be 'simulator' or 'real', was '"
                            + mode + "'");
        }
        return mode;
    }

    /** True when {@link #executionMode()} is {@code real}. */
    public static boolean isReal() {
        return executionMode().equals("real");
    }

    /** Key prefix of the active section: {@code simulator.} or {@code real.}. */
    private static String section() {
        return isReal() ? "real." : "simulator.";
    }

    // ---------------------------------------------------------------- app under test

    public static String bundleId() {
        return ConfigReader.get("bundleId");
    }

    /**
     * App Group the app shares with its widgets. The SwiftData store lives in
     * this group's container, not in the app's own data container.
     */
    public static String appGroupId() {
        return ConfigReader.getOrDefault("appGroupId", "group." + bundleId());
    }

    /**
     * Path to a built bundle. On simulator a blank value means "attach to the
     * installed app by {@link #bundleId()}"; on hardware a signed
     * {@code .ipa} is required (see {@link #realAppPath()}).
     */
    public static String appPath() {
        return ConfigReader.getOrDefault("simulator.appPath", "");
    }

    /** Signed {@code .ipa} reinstalled every session on hardware. */
    public static String realAppPath() {
        return ConfigReader.getOrDefault("real.appPath", "");
    }

    // ---------------------------------------------------------------- device

    /**
     * The device profile in use, or null when the {@code simulator.*} /
     * {@code real.*} keys describe the device directly: {@code device} if set
     * for the run, else {@code simulator.device} / {@code real.device}.
     */
    public static String deviceProfile() {
        String device = ConfigReader.getOrDefault("device", null);
        if (device != null) {
            return device;
        }
        String profile = ConfigReader.getOrDefault(section() + "device", null);
        if (profile != null && !DeviceProfile.type(profile).equals(executionMode())) {
            throw new IllegalStateException(section() + "device=" + profile + " is a "
                    + DeviceProfile.type(profile) + " profile");
        }
        return profile;
    }

    public static String deviceName() {
        return deviceValue("deviceName");
    }

    public static String platformVersion() {
        return deviceValue("platformVersion");
    }

    public static String udid() {
        return deviceValue("udid");
    }

    /**
     * A device detail: an explicit {@code -Diosautomation.<section>.<key>} first, then
     * the device profile, then {@code <section>.<key>} in config.properties.
     */
    private static String deviceValue(String key) {
        String explicit = ConfigReader.override(section() + key);
        if (explicit != null) {
            return explicit;
        }
        String profile = deviceProfile();
        return profile != null ? DeviceProfile.require(profile, key) : ConfigReader.get(section() + key);
    }

    /**
     * WebDriverAgent port for sessions this suite creates.
     *
     * <p>Deliberately <b>not</b> 8100. Appium Inspector keeps a live session on
     * 8100 while you are poking at the UI by hand, and two WDA instances cannot
     * share a port — a default that collides makes {@code DriverFactory} fail with
     * an opaque "port already in use" only on machines with Inspector open.
     *
     * <p>{@code simulator.wdaLocalPort} / {@code real.wdaLocalPort} override the
     * shared value per target. The real device needs its own: a simulator WDA
     * stays bound to its port on this Mac after its session ends, and Appium
     * refuses to forward a device's WDA onto a port that is already taken
     * ("The port #8102 is occupied by an other process").
     */
    public static int wdaLocalPort() {
        String explicit = ConfigReader.override(section() + "wdaLocalPort");
        String profile = deviceProfile();
        String fromProfile = profile == null ? null : DeviceProfile.value(profile, "wdaLocalPort");
        if (explicit == null && fromProfile != null) {
            try {
                return Integer.parseInt(fromProfile);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Device profile '" + profile
                        + "': wdaLocalPort must be an integer, was '" + fromProfile + "'", e);
            }
        }
        return ConfigReader.getInt(section() + "wdaLocalPort",
                ConfigReader.getInt("wdaLocalPort", 8102));
    }

    // ---------------------------------------------------------------- appium server

    public static String appiumUrl() {
        return ConfigReader.getOrDefault("appiumUrl", "http://127.0.0.1:4723");
    }

    /**
     * How long the server tolerates a gap between commands before assuming the
     * session died. Generous by design: step 25 blocks on a live AI call to the
     * production backend, which can stall well past any short timeout.
     */
    public static int newCommandTimeout() {
        return ConfigReader.getInt("newCommandTimeout", 240);
    }

    // ---------------------------------------------------------------- locale

    /**
     * The app ships 29 localisations and exposes <b>no</b> accessibility identifiers,
     * so every locator in {@link com.portfolio.ios.pages.Locators} matches on visible text.
     * Forcing English at session level is what makes those locators deterministic.
     */
    public static String language() {
        return ConfigReader.getOrDefault("language", "en");
    }

    public static String locale() {
        return ConfigReader.getOrDefault("locale", "en_US");
    }

    // ---------------------------------------------------------------- signing (real device)

    /**
     * Apple Developer Team ID that signs WebDriverAgent on hardware
     * (Xcode → Settings → Accounts → Team, or developer.apple.com).
     * Required for real-device runs; unused on simulator.
     */
    public static String xcodeOrgId() {
        return ConfigReader.getOrDefault("real.xcodeOrgId", "");
    }

    /** Signing identity for WebDriverAgent (default works for most teams). */
    public static String xcodeSigningId() {
        return ConfigReader.getOrDefault("real.xcodeSigningId", "Apple Development");
    }

    /**
     * Custom bundle id for the signed WebDriverAgentRunner, only needed when
     * the team already has a wildcard/conflicting WDA install.
     */
    public static String updatedWdaBundleId() {
        return ConfigReader.getOrDefault("real.updatedWdaBundleId", "");
    }

    /**
     * Lets Xcode update WebDriverAgent's provisioning profile and register the
     * phone with the team while building WDA ({@code -allowProvisioningUpdates
     * -allowProvisioningDeviceRegistration}). Needed once for a phone WDA has
     * not run on ("… doesn't include the currently selected device"). Off by
     * default: registering a phone changes the developer account and counts
     * toward the team's device limit.
     */
    public static boolean allowProvisioningDeviceRegistration() {
        return ConfigReader.getBool("real.allowProvisioningDeviceRegistration", false);
    }

    /** Reuse a previously built WebDriverAgent instead of rebuilding per run. */
    public static boolean usePrebuiltWda() {
        return ConfigReader.getBool("real.usePrebuiltWda", false);
    }

    /**
     * SHA-1 of the signing certificate WebDriverAgent is re-signed with.
     *
     * <p>Needed when the keychain holds several valid certificates with the same
     * name. WDA's "Embed app icon into Runner.app" post-build step re-signs the
     * runner by certificate <i>name</i>; with duplicates, codesign rejects the
     * name as ambiguous and xcodebuild exits 65, leaving a runner the phone
     * refuses with MIInstallerErrorDomain 13. A SHA-1 is never ambiguous.
     * Blank means "let the name resolve" (fine when the name is unique).
     */
    public static String wdaSigningCertSha1() {
        return ConfigReader.getOrDefault("real.wdaSigningCertSha1", "");
    }

    /**
     * DerivedData folder used only for the real-device WDA build. Blank means
     * Xcode's shared default, which an open Xcode window and the simulator
     * WDA build also write to.
     */
    public static String wdaDerivedDataPath() {
        return ConfigReader.getOrDefault("real.wdaDerivedDataPath", "");
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * How a test gets a clean, mid-onboarding app:
     * {@code defaults} rewrites the {@code hasCompletedOnboarding} flag in the app's
     * UserDefaults domain (fast, keeps the container), {@code reinstall} tears the
     * app down and installs the bundle again (slow, bulletproof).
     */
    public static String resetStrategy() {
        return ConfigReader.getOrDefault("resetStrategy", "defaults");
    }

    public static boolean reinstallOnReset() {
        return "reinstall".equalsIgnoreCase(resetStrategy());
    }

    public static int implicitTimeoutSeconds() {
        return ConfigReader.getInt("implicitTimeoutSeconds", 0);
    }

    // ---------------------------------------------------------------- artifacts

    public static Path artifactsDir() {
        return Paths.get(ConfigReader.getOrDefault("artifactsDir", "target/artifacts"))
                .toAbsolutePath();
    }

    // ---------------------------------------------------------------- local folders

    /**
     * Where the suite keeps state between runs — the onboarded baselines.
     * {@code stateDir}; default {@code ~/.iosautomation-automation}.
     */
    public static Path stateDir() {
        return Paths.get(ConfigReader.getOrDefault("stateDir",
                Paths.get(System.getProperty("user.home"), ".iosautomation-automation").toString()));
    }

    /**
     * The folder holding the simulators' data — Xcode's default unless the
     * simulators live in a custom device set ({@code simulator.devicesDir}).
     */
    public static Path simulatorDevicesDir() {
        return Paths.get(ConfigReader.getOrDefault("simulator.devicesDir",
                Paths.get(System.getProperty("user.home"), "Library", "Developer", "CoreSimulator", "Devices")
                        .toString()));
    }

    /** The active simulator's data folder ({@code <devicesDir>/<udid>/data}). */
    public static Path simulatorDataDir() {
        return simulatorDevicesDir().resolve(udid()).resolve("data");
    }

    // ---------------------------------------------------------------- plumbing

    /** The resolved configuration, for attaching to test reports. */
    public static Map<String, String> describe() {
        Map<String, String> m = new HashMap<>();
        m.put("executionMode", executionMode());
        String profile = deviceProfile();
        m.put("device", profile == null ? "<" + section() + "* keys>" : profile);
        m.put("platform", ConfigReader.getOrDefault("platform", "iOS"));
        m.put("automationName", ConfigReader.getOrDefault("automationName", "XCUITest"));
        m.put("bundleId", bundleId());
        m.put("deviceName", deviceName());
        m.put("platformVersion", platformVersion());
        m.put("udid", udid());
        m.put("appiumUrl", appiumUrl());
        m.put("wdaLocalPort", String.valueOf(wdaLocalPort()));
        m.put("language", language());
        m.put("locale", locale());
        m.put("resetStrategy", resetStrategy());
        m.put("xcodeOrgId", xcodeOrgId().isBlank() ? "<unset>" : "<set>");
        return m;
    }
}
