package com.portfolio.ios.core;

import io.appium.java_client.ios.options.XCUITestOptions;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Proves capability selection follows {@code executionMode} — without an
 * Appium server or a phone.
 *
 * <p>{@link DriverFactory#buildOptions()} is driver-free, so these tests feed
 * it synthetic {@code config.properties} content through
 * {@code ConfigReader.loadForTests} and assert the resulting capabilities for
 * each mode. Switching sections must never leak values across modes (a real
 * UDID in a simulator session, or signing keys where they don't belong).
 */
@Story("Harness: configuration")
public class DriverConfigTest {

    @AfterMethod(alwaysRun = true)
    public void dropTestConfig() {
        ConfigReader.resetForTests();
    }

    @Test(groups = {"config", "fast"})
    @Description("Simulator mode selects the simulator section and no signing keys")
    public void simulatorModeSelectsSimulatorCapabilities() {
        ConfigReader.loadForTests(file(
                "executionMode", "simulator",
                "platform", "iOS",
                "automationName", "XCUITest",
                "bundleId", "com.portfolio.iosorie.mkapp",
                "simulator.deviceName", "iPhone 16 Pro",
                "simulator.platformVersion", "27.0",
                "simulator.udid", "SIM-UDID",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.xcodeOrgId", "TEAMID",
                "wdaLocalPort", "8102",
                "real.wdaLocalPort", "8103",
                "real.wdaSigningCertSha1", SHA1,
                "real.wdaDerivedDataPath", "/tmp/wda-real"));

        XCUITestOptions options = DriverFactory.buildOptions();

        assertEquals(cap(options, "deviceName"), "iPhone 16 Pro");
        assertEquals(cap(options, "udid"), "SIM-UDID");
        assertEquals(cap(options, "platformVersion"), "27.0");
        assertEquals(cap(options, "bundleId"), "com.portfolio.iosorie.mkapp");
        assertNull(cap(options, "xcodeOrgId"),
                "signing keys must not leak into simulator capabilities");
        assertNull(cap(options, "fullReset"),
                "simulator sessions must not full-reset");
        assertEquals(cap(options, "wdaLocalPort"), 8102,
                "simulator keeps the shared WDA port; real.wdaLocalPort must not leak");
        assertNull(cap(options, "xcodeConfigFile"),
                "WDA re-signing config must not leak into simulator capabilities");
        assertNull(cap(options, "derivedDataPath"),
                "simulator WDA keeps Xcode's default DerivedData");
        assertTrue(String.valueOf(cap(options, "processArguments")).contains("-disableReviewPrompt"),
                "the app's review prompt must be switched off for every session");
    }

    @Test(groups = {"config", "fast"})
    @Description("Real mode pins WDA's signing cert by SHA-1, isolates its build and port")
    public void realModeConfiguresWdaSigningBuildAndPort() throws Exception {
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "bundleId", "com.portfolio.iosorie.mkapp",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.appPath", "/tmp/CalCare.ipa",
                "real.xcodeOrgId", "TEAMID",
                "wdaLocalPort", "8102",
                "real.wdaLocalPort", "8103",
                "real.wdaSigningCertSha1", SHA1.toLowerCase(),
                "real.wdaDerivedDataPath", "/tmp/wda-real"));

        XCUITestOptions options = DriverFactory.buildOptions();

        assertEquals(cap(options, "wdaLocalPort"), 8103);
        assertEquals(cap(options, "derivedDataPath"), "/tmp/wda-real");
        assertEquals(cap(options, "clearSystemFiles"), Boolean.FALSE,
                "WDA build logs must survive the session for diagnosis");
        assertNull(cap(options, "updatedWDABundleId"),
                "unset updatedWdaBundleId must leave WDA's own bundle id alone");
        String xcconfig = java.nio.file.Files.readString(
                java.nio.file.Path.of(String.valueOf(cap(options, "xcodeConfigFile"))));
        assertTrue(xcconfig.contains("EXPANDED_CODE_SIGN_IDENTITY = " + SHA1),
                "xcconfig must pin the upper-cased SHA-1, was: " + xcconfig);
    }

    @Test(groups = {"config", "fast"})
    @Description("A malformed WDA signing SHA-1 fails naming the property")
    public void realModeWithMalformedSigningShaFailsFast() {
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.appPath", "/tmp/CalCare.ipa",
                "real.xcodeOrgId", "TEAMID",
                "real.wdaSigningCertSha1", "Apple Development: Someone"));

        IllegalStateException e = expectThrows(IllegalStateException.class,
                DriverFactory::buildOptions);
        assertTrue(e.getMessage().contains("real.wdaSigningCertSha1"),
                "failure must name the bad property, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("Real mode selects the real section plus signing and full reset")
    public void realModeSelectsRealCapabilities() {
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "bundleId", "com.portfolio.iosorie.mkapp",
                "simulator.deviceName", "iPhone 16 Pro",
                "simulator.platformVersion", "27.0",
                "simulator.udid", "SIM-UDID",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.appPath", "/tmp/CalCare.ipa",
                "real.xcodeOrgId", "TEAMID",
                "real.xcodeSigningId", "Apple Development"));

        XCUITestOptions options = DriverFactory.buildOptions();

        assertEquals(cap(options, "deviceName"), "iPhone15 Pro");
        assertEquals(cap(options, "udid"), "REAL-UDID");
        assertEquals(cap(options, "app"), "/tmp/CalCare.ipa");
        assertEquals(cap(options, "fullReset"), Boolean.TRUE);
        assertTrue(String.valueOf(cap(options, "xcodeOrgId")).contains("TEAMID"),
                "real capabilities must carry the signing Team ID");
    }

    @Test(groups = {"config", "fast"})
    @Description("Real mode without an .ipa fails with the exact missing piece")
    public void realModeWithoutAppPathFailsFast() {
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.xcodeOrgId", "TEAMID"));

        IllegalStateException e = expectThrows(IllegalStateException.class,
                DriverFactory::buildOptions);
        assertTrue(e.getMessage().contains("real.appPath"),
                "failure must name the missing property, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("Real mode without a Team ID fails with the exact missing piece")
    public void realModeWithoutTeamIdFailsFast() {
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "real.deviceName", "iPhone15 Pro",
                "real.platformVersion", "27.0",
                "real.udid", "REAL-UDID",
                "real.appPath", "/tmp/CalCare.ipa"));

        IllegalStateException e = expectThrows(IllegalStateException.class,
                DriverFactory::buildOptions);
        assertTrue(e.getMessage().contains("real.xcodeOrgId"),
                "failure must name the missing property, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("An unknown executionMode fails instead of driving the wrong phone")
    public void unknownExecutionModeFailsFast() {
        ConfigReader.loadForTests(file("executionMode", "tablet"));

        IllegalStateException e = expectThrows(IllegalStateException.class,
                Config::executionMode);
        assertTrue(e.getMessage().contains("tablet"),
                "failure must echo the bad value, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("A missing required property fails naming the exact key")
    public void missingRequiredPropertyFailsFast() {
        ConfigReader.loadForTests(new Properties());

        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> ConfigReader.get("simulator.udid"));
        assertTrue(e.getMessage().contains("simulator.udid"),
                "failure must name the missing key, was: " + e.getMessage());
    }

    // ---------------------------------------------------------------- device profiles

    @Test(groups = {"config", "fast"})
    @Description("real.device names the phone: its profile supplies name, UDID and iOS version")
    public void realDeviceComesFromItsProfile() {
        String profile = firstProfile("real");
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "bundleId", "com.portfolio.iosorie.mkapp",
                "real.device", profile,
                "real.appPath", "/tmp/CalCare.ipa",
                "real.xcodeOrgId", "TEAM123456"));

        XCUITestOptions options = DriverFactory.buildOptions();

        assertEquals(Config.deviceProfile(), profile);
        assertEquals(cap(options, "udid"), DeviceProfile.require(profile, "udid"));
        assertEquals(cap(options, "deviceName"), DeviceProfile.require(profile, "deviceName"));
        assertEquals(cap(options, "platformVersion"), DeviceProfile.require(profile, "platformVersion"));
    }

    @Test(groups = {"config", "fast"})
    @Description("device=<name> picks that device for the run, and its type sets the mode")
    public void deviceSelectsItsMode() {
        String profile = firstProfile("simulator");
        ConfigReader.loadForTests(file(
                "executionMode", "real",
                "bundleId", "com.portfolio.iosorie.mkapp",
                "device", profile));

        assertEquals(Config.executionMode(), "simulator", "the profile's type wins over the file's mode");
        assertEquals(Config.udid(), DeviceProfile.require(profile, "udid"));
    }

    @Test(groups = {"config", "fast"})
    @Description("A real phone's profile set as the simulator device fails instead of driving the wrong target")
    public void profileOfTheWrongTypeFailsFast() {
        String profile = firstProfile("real");
        ConfigReader.loadForTests(file(
                "executionMode", "simulator",
                "simulator.device", profile));

        IllegalStateException e = expectThrows(IllegalStateException.class, Config::udid);
        assertTrue(e.getMessage().contains("real profile"),
                "failure must say the profile is for a real device, was: " + e.getMessage());
    }

    @Test(groups = {"config", "fast"})
    @Description("An unknown device name fails listing the profiles that exist")
    public void unknownProfileListsTheAvailableOnes() {
        ConfigReader.loadForTests(file("device", "no-such-phone"));

        IllegalStateException e = expectThrows(IllegalStateException.class, Config::executionMode);
        assertTrue(e.getMessage().contains("no-such-phone") && e.getMessage().contains(firstProfile("real")),
                "failure must name the bad device and list the real ones, was: " + e.getMessage());
    }

    /** A committed profile of {@code type}, so these tests follow whatever devices are configured. */
    private static String firstProfile(String type) {
        return DeviceProfile.available().stream()
                .filter(name -> DeviceProfile.type(name).equals(type))
                .findFirst()
                .orElseThrow(() -> new org.testng.SkipException("No " + type + " device profile in src/test/resources/devices"));
    }

    // ---------------------------------------------------------------- helpers

    private static final String SHA1 = "A3C7B6C27B6E1DBB34C49A061267EF4C3C0D2DB8";

    private static Object cap(XCUITestOptions options, String shortName) {
        return options.getCapability("appium:" + shortName);
    }

    private static Properties file(String... keyValues) {
        Properties props = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            props.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return props;
    }
}
