package com.portfolio.ios.tests.home;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.model.ExpectedTargets;
import com.portfolio.ios.model.OnboardingOptions;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.Locators;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Module 1 — the Home dashboard of a freshly onboarded user.
 *
 * <p>Read-only: no test here creates data, so all ten share the one onboarded
 * state from {@link AbstractAppTest} and each starts back on Home.
 *
 * <p>Groups: {@code home} for the module, {@code smoke} for P0, {@code regression}
 * for everything, and {@code p0}…{@code p3} for priority.
 */
@Epic("Home")
@Feature("Dashboard")
public class HomeDashboardTest extends AbstractAppTest {

    @Test(groups = {"home", "smoke", "regression", "p0"},
            description = "HOME_001 Home loads after onboarding")
    @Description("Header shows today, the calorie card shows Remaining equal to the goal "
            + "(nothing logged yet), and all four tabs are present")
    public void home001_loadsAfterOnboarding() {
        HomePage home = home();
        LocalDate today = home.deviceToday();

        assertEquals(home.headerTitle(), HomePage.todayTitle(today), "header title");
        assertTrue(HomePage.GREETINGS.contains(home.greeting()),
                "greeting must be one of " + HomePage.GREETINGS);

        int goal = home.goalKcal();
        assertTrue(goal > 0, "daily goal must be positive, was " + goal);
        assertEquals(home.remainingKcal(), goal,
                "with nothing logged, Remaining must equal the daily goal");
        assertTrue(Locators.MAIN_TABS.stream().allMatch(home::isTabVisible),
                "all tabs " + Locators.MAIN_TABS + " must be visible");
    }

    @Test(groups = {"home", "smoke", "regression", "p0"},
            description = "HOME_002 Dashboard sections for a new user")
    @Description("Every Home section is present with its first-run values: macros at 0 g, "
            + "onboarding weight, no water, no meals")
    public void home002_showsSectionsForNewUser() {
        HomePage home = home();
        SoftAssert soft = new SoftAssert();

        Map<String, HomePage.Macro> macros = home.macros();
        macros.forEach((name, macro) -> {
            soft.assertEquals(macro.eatenG(), 0, name + " eaten");
            soft.assertTrue(macro.targetG() > 0, name + " target must be positive, was " + macro.targetG());
        });

        soft.assertNotNull(home.scrollIntoView(Locators.staticText("Activities")), "Activities card");
        soft.assertNotNull(home.scrollIntoView(Locators.homeActivitiesAddButton()), "Activities 'Add' button");
        soft.assertNotNull(home.tileLabel("AI INSIGHT"), "AI INSIGHT banner");
        soft.assertNotNull(home.tileLabel("INTERMITTENT FASTING"), "INTERMITTENT FASTING row");

        // Exactly the onboarding weight with no delta: a "+0.0 kg" suffix would
        // mean a second weight entry, i.e. data leaking in from an earlier run.
        soft.assertEquals(home.tileLabel("WEIGHT"),
                String.format(Locale.US, "WEIGHT, %.1f, kg", profile().weightKg()), "WEIGHT tile");
        String mood = home.tileLabel("MOOD");
        soft.assertTrue(mood != null && mood.contains("Log mood"), "MOOD tile offers 'Log mood', was " + mood);
        soft.assertNotNull(home.tileLabel("STEPS"), "STEPS tile");

        soft.assertNotNull(home.scrollIntoView(Locators.staticText("Water")), "Water card");
        soft.assertNotNull(home.scrollIntoView(Locators.staticText("0/2000ml")),
                "Water starts at 0 of 8 glasses × 250 ml");
        soft.assertNotNull(home.scrollIntoView(Locators.staticText("Today's Meals")), "Today's Meals header");
        soft.assertNotNull(home.scrollIntoView(Locators.staticText("No meals logged yet")),
                "empty meals state");
        soft.assertAll();
    }

    @Test(groups = {"home", "regression", "p1"},
            description = "HOME_003 Daily goal matches the profile")
    @Description("Goal and macro targets equal the app's own formula for the onboarding profile. "
            + "Onboarding automation does not select a goal yet, so the app keeps Maintain Weight.")
    public void home003_goalMatchesProfileFormula() {
        HomePage home = home();
        ExpectedTargets expected = ExpectedTargets.of(profile(),
                OnboardingOptions.Goal.MAINTAIN_WEIGHT, 0, home.deviceToday().getYear());

        assertEquals(home.goalKcal(), expected.calories(), "daily calorie goal");
        Map<String, HomePage.Macro> macros = home.macros();
        assertEquals(macros.get("Protein").targetG(), expected.proteinG(), "protein target");
        assertEquals(macros.get("Carbs").targetG(), expected.carbsG(), "carbs target");
        assertEquals(macros.get("Fat").targetG(), expected.fatG(), "fat target");
    }

    @Test(groups = {"home", "regression", "p1"},
            description = "HOME_004 Goal opens Calorie Detail")
    @Description("The goal button opens Calorie Detail with Day/Week/Month/Year; "
            + "switching to Week retitles it; Done returns to Home")
    public void home004_goalOpensCalorieDetail() {
        HomePage home = home();
        home.openCalorieDetail();
        assertTrue(home.isCalorieDetailShowing(), "Day/Week/Month/Year picker and 'TODAY' caption");

        home.selectCalorieDetailRange("Week");
        home.closeCalorieDetail();
        assertTrue(home.isShowing(), "Done returns to Home");
    }

    @Test(groups = {"home", "regression", "p2"},
            description = "HOME_005 Goal info sheet opens and closes")
    @Description("The ⓘ button opens 'How your goal is set' with the Daily target row; Done closes it")
    public void home005_goalInfoSheet() {
        HomePage home = home();
        home.openGoalInfo();
        assertTrue(home.isGoalInfoShowing(), "'Daily target' row on the sheet");

        home.closeGoalInfo();
        assertFalse(home.isGoalInfoShowing(), "sheet closed");
        assertTrue(home.isShowing(), "back on Home");
    }

    @Test(groups = {"home", "regression", "p2"},
            description = "HOME_006 Streak badge opens streak info")
    @Description("A new user has 0 of 4 streaks; the badge opens 'Cal Care Streaks' and Close returns")
    public void home006_streakBadgeOpensInfo() {
        HomePage home = home();
        assertTrue(home.streakBadgeLabel().startsWith("0 of 4 streaks active today"),
                "streak badge for a new user, was: " + home.streakBadgeLabel());

        home.openStreakInfo();
        assertTrue(home.isStreakInfoShowing(), "streak sheet title");
        home.closeStreakInfo();
        assertTrue(home.isShowing(), "back on Home");
    }

    @Test(groups = {"home", "regression", "p1"},
            description = "HOME_007 Calendar cancel keeps today")
    @Description("The calendar opens 'Pick a Day'; Cancel closes it without changing the selected day")
    public void home007_calendarCancelKeepsToday() {
        HomePage home = home();
        String before = home.headerTitle();

        home.openCalendar();
        assertTrue(home.isCalendarShowing(), "calendar sheet");
        home.cancelCalendar();
        assertEquals(home.headerTitle(), before, "Cancel must not change the selected day");
    }

    @Test(groups = {"home", "regression", "p3"},
            description = "HOME_008 Future days cannot be selected")
    @Description("For a new user only today is selectable: tomorrow's pill is not a button "
            + "and tapping it keeps Home on today")
    public void home008_futureDayNotSelectable() {
        HomePage home = home();
        LocalDate today = home.deviceToday();
        LocalDate tomorrow = today.plusDays(1);

        assertEquals(home.dayPillType(today), "XCUIElementTypeButton", "today's pill is a button");
        String tomorrowType = home.dayPillType(tomorrow);
        assertNotNull(tomorrowType, "week strip shows tomorrow (" + HomePage.dayPillLabel(tomorrow) + ")");
        assertFalse("XCUIElementTypeButton".equals(tomorrowType), "tomorrow's pill must not be a button");

        home.tapDayPill(tomorrow);
        assertEquals(home.headerTitle(), HomePage.todayTitle(today), "tapping a future day changes nothing");
    }

    @Test(groups = {"home", "smoke", "regression", "p0"},
            description = "HOME_009 Tab navigation round trip")
    @Description("Meals, Insights and Profile each open their landing screen; Home comes back intact")
    public void home009_tabNavigationRoundTrip() {
        HomePage home = home();

        home.openTab("Meals");
        assertTrue(home.showsWithin(Locators.mealsLanding()), "Meals tab: 'Available Meal Plans'");
        home.openTab("Insights");
        assertTrue(home.showsWithin(Locators.insightsLanding()), "Insights tab: 'This Week vs Last Week'");
        home.openTab("Profile");
        assertTrue(home.showsWithin(Locators.profileLanding()), "Profile tab: 'About You'");

        home.openTab("Home");
        home.waitUntilLoaded();
        assertTrue(home.isShowing(), "Home header back");
    }

    @Test(groups = {"home", "regression", "p1"},
            description = "HOME_010 Relaunch lands on Home")
    @Description("After the app is terminated and relaunched it opens on Home, not onboarding, "
            + "with the same daily goal")
    public void home010_relaunchLandsOnHome() {
        HomePage home = home();
        int goalBefore = home.goalKcal();

        driver.terminateApp(Config.bundleId());
        driver.activateApp(Config.bundleId());

        home.waitUntilLoaded();
        assertFalse(com.portfolio.ios.core.OnboardingStep.WELCOME.isCurrentNow(driver),
                "relaunch must not show onboarding again");
        assertEquals(home.goalKcal(), goalBefore, "daily goal survives a relaunch");
    }
}
