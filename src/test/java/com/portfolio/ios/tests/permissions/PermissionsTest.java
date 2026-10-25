package com.portfolio.ios.tests.permissions;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.base.AppSession;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.pages.HealthAccessSheet;
import com.portfolio.ios.pages.NotificationSettingsPage;
import com.portfolio.ios.pages.ProfilePage;
import com.portfolio.ios.pages.ScannerPage;
import com.portfolio.ios.pages.SystemAlert;
import com.portfolio.ios.pages.SystemDialogPage;
import com.portfolio.ios.pages.VoiceInputPage;
import io.appium.java_client.appmanagement.ApplicationState;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 13 — what the app does when the user says no: notifications, Apple
 * Health, the camera, the microphone and speech recognition.
 *
 * <p>iOS asks for each permission once per install. On the simulator every
 * prompt is exercised each run: PERM_001 reinstalls the same build (the only
 * way iOS forgets notifications and Apple Health answers) and restores the
 * onboarded baseline; camera, microphone and speech are put back to "not
 * asked" with {@code simctl privacy reset all}, which the class also does
 * when it ends so the next Scanner visit asks again. Should Apple Health
 * still read as allowed, the simulator's Health store is reset (a reboot).
 * On a real device the tests start from whatever iOS recorded: a prompt not
 * yet answered is declined, an earlier decline is checked as is, and an
 * earlier "allow" skips.
 *
 * <p>On a real device the camera and voice checks are skipped: a decline
 * there lasts until the app is reinstalled and would block the scan tests.
 */
@Epic("Permissions")
@Feature("Permission declined")
public class PermissionsTest extends AbstractAppTest {

    private ProfilePage profileTab() {
        return new ProfilePage(driver, factory).openTab();
    }

    @AfterClass(alwaysRun = true)
    public void forgetPrivacyAnswers() {
        AppStateResetter.resetPrivacyPermissions();
    }

    @Test(groups = {"permissions", "regression", "p1"}, priority = 0,
            description = "PERM_001 Notifications turned down")
    @Description("With reminders on and notifications declined, Notifications says they are off in iOS Settings, "
            + "and its button opens the Settings app (iOS will not ask twice)")
    public void perm001_notificationsDeclined() {
        if (!Config.isReal()) {
            // iOS keeps the notifications answer until the app is reinstalled.
            assertTrue(AppStateResetter.reinstallInstalledApp(), "reinstalled the app");
            restoreBaseline();
            SystemAlert.answerIfShown(driver, "Notifications", false, Waits.QUICK);
        }
        NotificationSettingsPage notifications = profileTab().openNotifications();
        if ("Enable".equals(notifications.bannerAction())) {
            Allure.step("iOS has not asked yet: Enable, then decline the prompt");
            notifications.tapBannerAction();
            String prompt = SystemAlert.waitFor(driver, "Notifications", Waits.MEDIUM);
            assertTrue(prompt != null, "'Enable' shows the iOS notifications prompt");
            SystemAlert.answer(driver, prompt, false);
        }
        if (notifications.bannerAction() == null && !notifications.showsPermissionBanner()) {
            notifications.done();
            throw new SkipException("Notifications are allowed for the app; a test cannot take that back");
        }

        notifications.waitForBannerAction("Open Settings");
        assertTrue(notifications.showsPermissionBanner(), "'" + NotificationSettingsPage.PERMISSION_BANNER + "'");
        assertTrue(notifications.showsPermissionHint(), "'" + NotificationSettingsPage.PERMISSION_HINT + "'");

        notifications.tapBannerAction();
        Waits.untilTrue(Waits.MEDIUM, "the Settings app in front",
                () -> driver.queryAppState("com.apple.Preferences") == ApplicationState.RUNNING_IN_FOREGROUND);
        driver.activateApp(Config.bundleId());
        notifications.waitUntilLoaded();
        assertEquals(notifications.bannerAction(), "Open Settings", "back in the app, still off");
        notifications.done();
    }

    @Test(groups = {"permissions", "regression", "p1"}, priority = 1,
            description = "PERM_002 Apple Health turned down")
    @Description("Declining Apple Health from Profile puts the switch back off with 'Sync steps & active energy' "
            + "and explains how to allow it in Settings; turning it on again shows the same, without an iOS prompt")
    public void perm002_appleHealthDeclined() {
        ProfilePage profile = profileTab();
        assertFalse(profile.isAppleHealthOn(), "Apple Health starts off (onboarding skipped it)");

        profile.tapAppleHealth();
        declineHealthPromptIfShown();
        if (isConnectedAfterTap(profile)) {
            if (Config.isReal()) {
                throw new SkipException("Apple Health was allowed earlier; a device cannot reset that");
            }
            Allure.step("Apple Health was allowed earlier: reset the simulator's Health store and retry");
            restartWithFreshHealthPermission();
            profile = profileTab();
            profile.tapAppleHealth();
            assertTrue(declineHealthPromptIfShown(), "after the reset iOS asks again");
        }

        HealthAccessSheet sheet = new HealthAccessSheet(driver, factory).waitUntilDenied();
        assertTrue(sheet.showsDeniedText(), "how to allow it in Settings");
        assertFalse(sheet.hasContinue(), "nothing to retry from the app");
        profile = sheet.close();
        assertFalse(profile.isAppleHealthOn(), "the switch went back off");
        assertEquals(profile.appleHealthStatus(), "Sync steps & active energy", "text under Apple Health");

        profile.tapAppleHealth();
        assertFalse(declineHealthPromptIfShown(), "iOS does not ask a second time");
        new HealthAccessSheet(driver, factory).waitUntilDenied().close();
        profile.waitForAppleHealth(false);
    }

    @Test(groups = {"permissions", "regression", "p2"}, priority = 2,
            description = "PERM_003 Camera turned down")
    @Description("Declining the camera prompt covers the Scanner with 'Camera Access Needed', offering Open "
            + "Settings or Not Now; Not Now returns Home")
    public void perm003_cameraDeclined() {
        if (Config.isReal()) {
            throw new SkipException("A camera decline lasts until reinstall on a device and would block the scan tests");
        }
        AppStateResetter.resetPrivacyPermissions();
        ScannerPage scanner = relaunchApp().openScanner();

        String prompt = SystemAlert.waitFor(driver, "camera", Waits.MEDIUM);
        assertTrue(prompt != null, "the Scanner asks for the camera");
        SystemAlert.answer(driver, prompt, false);

        assertTrue(scanner.showsCameraAccessNeeded(), "'" + ScannerPage.CAMERA_ACCESS_NEEDED + "'");
        assertEquals(scanner.accessNeededText(), ScannerPage.CAMERA_ACCESS_TEXT, "its explanation");
        assertTrue(scanner.hasButton("Open Settings"), "'Open Settings'");
        assertFalse(scanner.hasButton("Choose from Library"), "no photo library offer when access is denied");
        scanner.notNow();
    }

    @Test(groups = {"permissions", "regression", "p2"}, priority = 3,
            description = "PERM_004 Speech recognition turned down")
    @Description("Declining speech recognition for Voice Input shows 'Microphone Access Needed' with Open "
            + "Settings and Cancel")
    public void perm004_voiceDeclined() {
        if (Config.isReal()) {
            throw new SkipException("A voice decline lasts until reinstall on a device");
        }
        AppStateResetter.resetPrivacyPermissions();
        VoiceInputPage voice = relaunchApp().openVoiceInput();

        voice.tapMic();
        String prompt = SystemAlert.waitFor(driver, "Speech Recognition", Waits.MEDIUM);
        assertTrue(prompt != null, "the mic asks for speech recognition first");
        SystemAlert.answer(driver, prompt, false);

        List<String> alert = voice.waitForAccessNeeded();
        assertEquals(alert, List.of(VoiceInputPage.ACCESS_NEEDED, VoiceInputPage.ACCESS_TEXT), "the app's alert");
        assertTrue(voice.alertHasButton("Open Settings"), "'Open Settings'");
        voice.cancelAlert().cancel();
    }

    // ---------------------------------------------------------------- Apple Health plumbing

    /** Declines the iOS Apple Health sheet if it shows; true if it did. */
    private boolean declineHealthPromptIfShown() {
        SystemDialogPage dialog = new SystemDialogPage(driver, factory);
        if (!dialog.isDialogShowing(Waits.SHORT)) {
            return false;
        }
        dialog.deny();
        dialog.acknowledgeRemainingAlert();
        return true;
    }

    /** After a tap with no sheet: did the switch stay on (allowed earlier) rather than show the denied sheet? */
    private boolean isConnectedAfterTap(ProfilePage profile) {
        return Waits.until(Waits.MEDIUM, "denied sheet or connected switch", () -> {
            if (Waits.isPresent(driver, com.portfolio.ios.pages.Locators.staticText(HealthAccessSheet.DENIED_TITLE))) {
                return Boolean.FALSE;
            }
            if (profile.isAppleHealthOn() && "Connected".equals(profile.appleHealthStatus())) {
                return Boolean.TRUE;
            }
            return null;
        });
    }

    /**
     * Resets the simulator's Health store, which needs a reboot: the shared
     * session ends with it, so a new one is started and the onboarded
     * baseline restored.
     */
    private void restartWithFreshHealthPermission() {
        AppSession.quit();
        if (!AppStateResetter.resetHealthKitAuthorization()) {
            throw new IllegalStateException("Could not reset the simulator's Health store");
        }
        factory = AppSession.factory();
        driver = factory.driver();
        restoreBaseline();
    }
}
