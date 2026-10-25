package com.portfolio.ios.tests.profile;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.model.ExpectedTargets;
import com.portfolio.ios.model.OnboardingOptions;
import com.portfolio.ios.model.Profile;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.Locators;
import com.portfolio.ios.pages.NotificationSettingsPage;
import com.portfolio.ios.pages.ProfilePage;
import com.portfolio.ios.pages.ResetGoalsPage;
import com.portfolio.ios.pages.SettingsPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Module 9 — Profile and Settings: the onboarding data on Profile, the
 * notification switches, the theme, the Settings sheets, and Reset Goals.
 *
 * <p>Profile shows exactly what onboarding saved for the default test profile
 * — including "Maintain Weight" next to a 65 kg target, confirmed as expected:
 * onboarding leaves the goal type on its Maintain default. Tests that change a
 * setting (theme, a reminder) put it back themselves.
 *
 * <p>PROF_011 completes Reset Goals, which rebuilds the meal plan on the
 * production AI backend; it is in the {@code ai} group, run on demand with
 * {@code -Dgroups=ai}, and runs last because it changes the profile.
 */
@Epic("Profile")
@Feature("Profile & settings")
public class ProfileSettingsTest extends AbstractAppTest {

    private ProfilePage profilePage() {
        return new ProfilePage(driver, factory).openTab();
    }

    // ---------------------------------------------------------------- Profile

    @Test(groups = {"profile", "smoke", "regression", "p0"},
            description = "PROF_001 Profile shows the onboarding data")
    @Description("About You, Body Metrics, Diet, Daily Goals (equal to Home's), My Goal, Daily Routine and the "
            + "Apple Health switch match what onboarding saved for the default profile")
    public void prof001_profileShowsOnboardingData() {
        HomePage home = home();
        int goal = home.goalKcal();
        Map<String, HomePage.Macro> macros = home.macros();
        int age = home.deviceToday().getYear() - profile().birthYear();

        ProfilePage profile = profilePage();
        assertEquals(profile.textAfter("Age", 1), age + " yrs");
        assertEquals(profile.textAfter("Sex", 1), "Female");
        assertEquals(profile.valueBefore("cm"), "170");
        assertEquals(profile.valueBefore("kg"), "70.0");
        assertEquals(profile.textAfter("BMI", 1), "24.2");
        assertEquals(profile.textAfter("BMI", 2), "Normal");
        assertEquals(profile.textAfter("Diet Preferences", 1), "Omnivore");

        Map<String, String> goals = profile.dailyGoals();
        assertEquals(goals.get("Calories"), goal + " kcal", "same goal as Home");
        assertEquals(goals.get("Protein"), macros.get("Protein").targetG() + "g");
        assertEquals(goals.get("Carbs"), macros.get("Carbs").targetG() + "g");
        assertEquals(goals.get("Fat"), macros.get("Fat").targetG() + "g");

        assertEquals(profile.textAfter("My Goal", 1), "Maintain Weight");
        assertEquals(profile.textAfter("Target weight", 1), "−5.0 kg");
        assertEquals(profile.textAfter("Target weight", 2), "65.0 kg");
        assertEquals(profile.textAfter("Weekly pace", 1), "Maintain");
        assertEquals(profile.textAfter("Activity", 1), "Moderately Active");
        assertEquals(profile.textAfter("Eating window", 1), "8 AM – 8 PM");
        assertEquals(profile.commitments(), List.of("Track my calories"));

        // The Apple Health permission is device state, so only its consistency is checked.
        if (profile.isAppleHealthOn()) {
            assertEquals(profile.appleHealthStatus(), "Connected");
        } else {
            assertNotEquals(profile.appleHealthStatus(), "Connected");
        }
    }

    @Test(groups = {"profile", "regression", "p1"},
            description = "PROF_002 Notification switches")
    @Description("Four reminders with their defaults; turning Water on survives closing and reopening, and is put "
            + "back. The 'Notifications are off' banner shows only when iOS has notifications off")
    public void prof002_notificationSwitches() {
        NotificationSettingsPage notifications = profilePage().openNotifications();
        assertTrue(notifications.isOn("Daily streak reminder"), "streak reminder on by default");
        assertTrue(notifications.isOn("Meal logging reminders"), "meal reminders on by default");
        assertFalse(notifications.isOn("Water reminders"), "water reminders off by default");
        assertTrue(notifications.isOn("Catch-up reminder"), "catch-up reminder on by default");
        boolean bannerAtStart = notifications.showsPermissionBanner();

        try {
            notifications.toggle("Water reminders");
            assertTrue(notifications.isOn("Water reminders"));
            notifications = notifications.done().openNotifications();
            assertTrue(notifications.isOn("Water reminders"), "Water stays on after reopening");
            assertEquals(notifications.showsPermissionBanner(), bannerAtStart,
                    "the banner follows iOS permission, not the switches");
        } finally {
            if (notifications.isOn("Water reminders")) {
                notifications.toggle("Water reminders");
            }
        }
        assertFalse(notifications.isOn("Water reminders"), "put back off");
        notifications.done();
    }

    // ---------------------------------------------------------------- Settings

    @Test(groups = {"profile", "smoke", "regression", "p1"},
            description = "PROF_003 Settings screen")
    @Description("The gear opens Settings: the three theme buttons, the ten rows in order and an App Version; "
            + "Close returns to Profile")
    public void prof003_settingsScreen() {
        SettingsPage settings = profilePage().openSettings();
        for (String theme : List.of("Dark", "Light", "System")) {
            assertTrue(settings.hasThemeButton(theme), "theme button " + theme);
        }
        assertEquals(settings.rows(), SettingsPage.ROWS, "Settings rows");
        assertTrue(settings.appVersion().matches("[0-9]+\\.[0-9]+(\\.[0-9]+)?"),
                "App Version: " + settings.appVersion());
        settings.close();
    }

    @Test(groups = {"profile", "regression", "p1"},
            description = "PROF_004 Theme")
    @Description("Dark darkens the app and survives a relaunch; Light makes it bright; System is always restored")
    public void prof004_theme() {
        SettingsPage settings = profilePage().openSettings();
        try {
            settings.chooseTheme("Dark");
            assertTrue(settings.looksDark(), "Settings in Dark");
            settings.close();
            assertTrue(new ProfilePage(driver, factory).looksDark(), "Profile in Dark");

            driver.terminateApp(Config.bundleId());
            HomePage home = relaunchApp();
            assertTrue(home.looksDark(), "Dark survives a relaunch: brightness " + home.screenBrightness());

            settings = profilePage().openSettings().chooseTheme("Light");
            assertFalse(settings.looksDark(), "Settings in Light");
        } finally {
            settings = new SettingsPage(driver, factory);
            if (!Waits.isPresent(driver, Locators.staticText("App Theme"))) {
                home().open();
                settings = profilePage().openSettings();
            }
            settings.chooseTheme("System");
        }
        settings.close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_005 Language list")
    @Description("Language lists English and Arabic with 'System default, English' selected; Done changes nothing")
    public void prof005_languageList() {
        SettingsPage settings = profilePage().openSettings().openLanguage();
        assertEquals(settings.selectedLanguage(), "System default, English");
        assertTrue(settings.listsLanguage("🇬🇧, English"), "English listed");
        assertTrue(settings.listsLanguage("🇸🇦, Arabic"), "Arabic listed");
        settings.doneSheet();
        assertTrue(settings.showsText("App Theme"), "still in English");
        settings.close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_006 Sources & References")
    @Description("Sources & References shows its disclosure and sections; Done closes it")
    public void prof006_sourcesAndReferences() {
        SettingsPage settings = profilePage().openSettings().openSources();
        assertTrue(settings.showsText("Sources & References"));
        assertTrue(settings.showsText("The information you provide is sent to third-party AI services "
                + "(ChatGPT or Kimi) to generate your plan."), "AI disclosure");
        assertTrue(settings.showsText("Results are estimates, not medical advice, diagnosis or treatment."),
                "medical disclaimer");
        settings.doneSheet().close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_007 Support")
    @Description("Without Mail (the simulator) Support says 'Mail Not Available' and names products@example.test; "
            + "on an iPhone the Mail app opens the draft and the test returns to the app — nothing is ever sent")
    public void prof007_support() {
        SettingsPage settings = profilePage().openSettings();
        String outcome = settings.tapSupport();
        if (!"composer".equals(outcome)) {
            assertEquals(outcome,
                    "No mail account is configured on this device. Reach us at products@example.test.");
        }
        settings.close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_008 Privacy Policy, Terms of Service, About Us")
    @Description("Each opens its titled page from the production website (needs network) and closes again")
    public void prof008_webPages() {
        SettingsPage settings = profilePage().openSettings();
        for (String page : List.of("Privacy Policy", "Terms of Service", "About Us")) {
            String heading = settings.openWebPage(page);
            assertFalse(heading.isBlank(), page + " loaded");
            settings.closeWebPage();
        }
        settings.close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_009 Widgets")
    @Description("The Widgets sheet opens from Profile and from Settings and closes with Done")
    public void prof009_widgets() {
        ProfilePage profile = profilePage();
        profile.openWidgets();
        new SettingsPage(driver, factory).closeWidgets();
        profile.waitUntilLoaded().openSettings().openWidgets().closeWidgets();
        new SettingsPage(driver, factory).close();
    }

    @Test(groups = {"profile", "regression", "p2"},
            description = "PROF_010 Reset Goals can be closed")
    @Description("The pencil opens Reset Goals at 'What is Your Gender?'; Close leaves Profile unchanged")
    public void prof010_resetGoalsCanBeClosed() {
        ProfilePage profile = profilePage();
        String weightBefore = profile.valueBefore("kg");
        ResetGoalsPage reset = profile.openResetGoals();
        for (String option : List.of("Female", "Male", "Prefer not to say")) {
            assertTrue(reset.offers(option), "option " + option);
        }
        profile = reset.close();
        assertEquals(profile.valueBefore("kg"), weightBefore, "nothing changed");
    }

    // ---------------------------------------------------------------- Reset Goals (AI backend)

    @Test(groups = {"profile", "ai", "regression", "p2"}, priority = 1,
            description = "PROF_011 Reset Goals with a new weight")
    @Description("Weight 70 → 72 kg through all steps, then Generate on the AI backend: 'Goal updated "
            + "successfully'; Profile shows 72.0 kg, BMI 24.9 and a −7.0 kg target; the daily goal is "
            + "recalculated for 72 kg on Profile and Home")
    public void prof011_resetGoalsWithNewWeight() {
        Profile heavier = Profile.builder().weightKg(72).build();
        ExpectedTargets expected = ExpectedTargets.of(heavier, OnboardingOptions.Goal.MAINTAIN_WEIGHT, 0,
                home().deviceToday().getYear());

        ResetGoalsPage reset = profilePage().openResetGoals()
                .continueTo("Birth Year")
                .continueTo("height")
                .continueTo("weight")
                .setWeightKg(72)
                .continueTo("Goal")
                .continueTo("desired")
                .continueTo("activity level")
                .continueTo("Dietary")
                .continueTo("Health Concerns")
                .pickAPlan();
        ProfilePage profile = reset.generate("Mediterranean Wellness", Waits.AI_BACKEND);

        assertEquals(profile.valueBefore("kg"), "72.0", "new weight");
        assertEquals(profile.textAfter("BMI", 1), "24.9");
        assertEquals(profile.textAfter("Target weight", 1), "−7.0 kg");
        assertEquals(profile.textAfter("Calories", 1), expected.calories() + " kcal", "goal recalculated");
        assertEquals(profile.textAfter("Protein", 1), expected.proteinG() + "g");

        HomePage home = home().open();
        assertEquals(home.goalKcal(), expected.calories(), "Home shows the new goal");
    }
}
