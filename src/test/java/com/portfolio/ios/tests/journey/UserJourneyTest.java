package com.portfolio.ios.tests.journey;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.pages.CoachPage;
import com.portfolio.ios.pages.CoachPage.Turn;
import com.portfolio.ios.pages.FastingSetupPage;
import com.portfolio.ios.pages.FoodDetailPage;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.InsightsPage;
import com.portfolio.ios.pages.SystemAlert;
import com.portfolio.ios.pages.WaterPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Module 14 — one user's first day, end to end, from a brand-new install:
 * onboarding, logging and editing a food, water, starting a fast, a question
 * to the coach, Insights, and a relaunch that must keep all of it.
 *
 * <p>Each step is covered in depth by its own module; this test proves they
 * work together and that what one screen saves, the others show. It starts
 * from wiped app data (a fresh install on a real device), so it does not use
 * the onboarded baseline the module tests share.
 */
@Epic("End-to-end")
@Feature("First day")
public class UserJourneyTest extends AbstractOnboardingTest {

    private static final int BANANA = 105;

    @Test(groups = {"journey", "regression", "p0"}, timeOut = 1_800_000,
            description = "JOURNEY_001 A new user's first day")
    @Description("Onboard; log a banana for breakfast and edit it to two; add a glass of water; start a 16:8 fast; "
            + "ask the coach for a meal plan; check Insights; relaunch — everything is still there")
    public void journey001_firstDay() {
        AppStateResetter.wipeAppData();
        launch();

        Allure.step("Onboarding");
        assertTrue(flow().runAll(), "onboarding completes");
        // The first Home asks iOS for notifications once per install (HomeView.swift:459).
        SystemAlert.answerIfShown(driver, "Notifications", false, Waits.SHORT);
        HomePage home = new HomePage(driver, factory).waitUntilLoaded();
        int goal = home.goalKcal();
        assertEquals(home.remainingKcal(), goal, "nothing eaten yet: Remaining = Goal");

        Allure.step("Log a banana for breakfast, then make it two");
        home = home.openFoodSearch().search("banana").open("Banana").selectMeal("Breakfast").log();
        assertEquals(home.remainingKcal(), goal - BANANA, "Remaining after one banana");
        FoodDetailPage detail = home.openLoggedFood("Banana");
        detail = detail.edit().increase().save();
        assertEquals(detail.kcal(), 2 * BANANA, "two bananas");
        home = detail.back();
        assertEquals(home.remainingKcal(), goal - 2 * BANANA, "Remaining after two bananas");
        assertEquals(home.mealCardKcal("BREAKFAST"), 2 * BANANA, "Breakfast total");

        Allure.step("Drink a glass of water");
        WaterPage water = new WaterPage(driver, factory);
        water.add();
        assertEquals(water.tileTotal(), "250/2000ml", "one glass");

        Allure.step("Start a 16:8 fast");
        home = new HomePage(driver, factory).open();
        FastingSetupPage setup = home.openFastingSetup();
        setup = setup.isShowingEducation() ? setup.completeEducation() : setup.waitForProtocolPicker();
        home = setup.choosePreset("16:8").continueToSchedule().save();
        assertTrue(home.fastingCard().isShowing(), "the fasting card shows the running fast");

        Allure.step("Ask the coach for a meal plan");
        CoachPage coach = home.open().openCoach();
        Turn turn = coach.sendLocal("Make me a meal plan for the week");
        assertEquals(turn.reply(), "Meal plans live in the Meal Plans tab — open it to generate or edit yours.",
                "the coach points to the Meal Plans tab");
        home = coach.close();

        Allure.step("Insights counts today");
        List<Integer> weekly = new InsightsPage(driver, factory).openTab().weeklyDifferences();
        assertEquals(weekly.get(0), Integer.valueOf(2 * BANANA), "this week's calories");
        assertEquals(weekly.get(2), Integer.valueOf(1), "days logged this week");
        home = new HomePage(driver, factory).open();

        Allure.step("Relaunch: everything is kept");
        relaunch();
        home = new HomePage(driver, factory).waitUntilLoaded().settleAfterLog();
        assertEquals(home.remainingKcal(), goal - 2 * BANANA, "Remaining after relaunch");
        assertTrue(home.loggedRowLabel("Banana").contains(", " + 2 * BANANA + ", "), "Banana row: 210 kcal");
        assertTrue(home.fastingCard().isShowing(), "the fast is still running");
        assertEquals(new WaterPage(driver, factory).tileTotal(), "250/2000ml", "water kept");
        coach = home.open().openCoach();
        assertEquals(coach.lastTurn(), turn, "the coach chat is kept");
        coach.close();
    }

    /** Terminates and launches with the session's arguments (English, review prompt off). */
    private void relaunch() {
        driver.executeScript("mobile: terminateApp", Map.of("bundleId", Config.bundleId()));
        launch();
    }

    private void launch() {
        Map<String, Object> params = new HashMap<>();
        params.put("bundleId", Config.bundleId());
        params.put("arguments", List.of("-disableReviewPrompt",
                "-AppleLanguages", "(" + Config.language() + ")",
                "-AppleLocale", Config.locale()));
        driver.executeScript("mobile: launchApp", params);
    }
}
