package com.portfolio.ios.tests.trackers;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.Locators;
import com.portfolio.ios.pages.MoodPage;
import com.portfolio.ios.pages.WaterPage;
import com.portfolio.ios.pages.WeightPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 5 — Home's water, weight and mood trackers.
 *
 * <p>One onboarded app for the class; no food is logged here, so the streak
 * badge only moves for water. Water tests start from 0 glasses (reset through
 * the app's own "−"); weight and mood tests compare before and after.
 *
 * <p>Changing weight does not recalculate the daily calorie goal — confirmed
 * as expected behaviour — so WEIGHT_001 guards that the goal stays put.
 */
@Epic("Trackers")
@Feature("Water, weight & mood")
public class HomeTrackersTest extends AbstractAppTest {

    private WaterPage water() {
        return new WaterPage(driver, factory);
    }

    private static String kgOrLbs(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    // ---------------------------------------------------------------- water

    @Test(groups = {"trackers", "smoke", "regression", "p0"},
            description = "WATER_001 Add and remove a glass")
    @Description("The tile's + adds 250 ml and − takes it back; − is disabled at 0")
    public void water001_addAndRemoveGlass() {
        WaterPage water = water().resetToZero();
        assertEquals(water.tileTotal(), "0/2000ml");
        assertFalse(water.isRemoveEnabled(), "− disabled at 0 glasses");

        water.add();
        assertEquals(water.tileTotal(), "250/2000ml", "one glass");
        assertTrue(water.isRemoveEnabled(), "− enabled once there is a glass");

        water.remove();
        assertEquals(water.tileTotal(), "0/2000ml", "back to 0");
        assertFalse(water.isRemoveEnabled(), "− disabled again at 0");
    }

    @Test(groups = {"trackers", "regression", "p1"},
            description = "WATER_002 Reaching the water goal")
    @Description("The 8th glass reaches 2000 ml, shows the water-goal celebration and ticks a streak; "
            + "a 9th glass keeps counting (2250/2250ml)")
    public void water002_reachingTheWaterGoal() {
        HomePage home = home();
        if (Config.isReal()) {
            // The app shows each streak's celebration once a day and remembers that in its
            // preferences (GamificationCoordinator: "streak.celebration.shown.<type>" = today),
            // which a phone's baseline restore cannot reset. For this launch only, read it as
            // not shown yet; the simulator restores the preferences with the baseline.
            home = relaunchAppWith("-streak.celebration.shown.Water Goal", "never");
        }
        WaterPage water = water().resetToZero();
        for (int glass = 1; glass <= 8; glass++) {
            water.add();
        }
        assertTrue(Waits.isVisible(driver, Locators.byExactLabel("You hit your water goal today."), Waits.SHORT),
                "water-goal celebration shown");
        assertEquals(water.tileTotal(), "2000/2000ml", "8 glasses");

        home.settleAfterLog();
        assertTrue(home.streakBadgeLabel().startsWith("1 of 4 streaks active today"),
                "water goal ticks one streak, was: " + home.streakBadgeLabel());

        water.add();
        assertEquals(water.tileTotal(), "2250/2250ml", "a 9th glass keeps counting past the goal");
    }

    @Test(groups = {"trackers", "regression", "p1"},
            description = "WATER_003 Water sheet")
    @Description("In the water sheet Add Glass and Undo change the count, tapping the 3rd glass sets it to 3, "
            + "and the Home tile then shows 750 ml")
    public void water003_waterSheet() {
        WaterPage water = water().resetToZero().openSheet();
        assertEquals(water.sheetCount(), 0);

        water.sheetAddGlass();
        assertEquals(water.sheetCount(), 1, "Add Glass");
        water.sheetUndo();
        assertEquals(water.sheetCount(), 0, "Undo");
        water.sheetTapGlass(3);
        assertEquals(water.sheetCount(), 3, "tapping the 3rd glass");

        water.closeSheet();
        assertEquals(water.tileTotal(), "750/2000ml", "Home tile matches the sheet");
    }

    @Test(groups = {"trackers", "regression", "p2"},
            description = "WATER_004 Water survives a relaunch")
    @Description("Two glasses are still there after the app is relaunched")
    public void water004_waterSurvivesRelaunch() {
        WaterPage water = water().resetToZero().add().add();
        assertEquals(water.tileTotal(), "500/2000ml");

        driver.terminateApp(Config.bundleId());
        relaunchApp();
        assertEquals(water().tileTotal(), "500/2000ml", "water survives a relaunch");
    }

    // ---------------------------------------------------------------- weight

    @Test(groups = {"trackers", "regression", "p1"},
            description = "WEIGHT_001 Log a new weight")
    @Description("+0.1 five times and Log Weight updates today's weight; the tile shows it and the daily "
            + "calorie goal is unchanged (expected behaviour)")
    public void weight001_logNewWeight() {
        HomePage home = home();
        int goalBefore = home.goalKcal();

        WeightPage weight = home.openWeight();
        double start = weight.toLog();
        weight.increase(5);
        double target = WeightPage.round1(start + 0.5);
        assertEquals(weight.toLog(), target, 0.001, "value after +0.5");
        assertEquals(weight.log(), "Updated today's weight", "today's onboarding entry is updated");
        assertEquals(weight.current(), target, 0.001, "CURRENT shows the logged weight");
        home = weight.done();

        assertTrue(home.tileLabel("WEIGHT").startsWith("WEIGHT, " + kgOrLbs(target) + ", kg"),
                "tile shows the new weight, was: " + home.tileLabel("WEIGHT"));
        home.scrollToTop();
        assertEquals(home.goalKcal(), goalBefore, "the daily goal does not change with weight");
    }

    @Test(groups = {"trackers", "regression", "p2"},
            description = "WEIGHT_002 Done without logging discards the change")
    @Description("Changing the value and closing with Done leaves the logged weight as it was")
    public void weight002_doneWithoutLoggingDiscards() {
        HomePage home = home();
        String tileBefore = home.tileLabel("WEIGHT");

        WeightPage weight = home.openWeight();
        double current = weight.current();
        weight.increase(3);
        assertEquals(weight.current(), current, 0.001, "CURRENT does not move before logging");
        home = weight.done();

        assertEquals(home.tileLabel("WEIGHT"), tileBefore, "tile unchanged after Done");
    }

    @Test(groups = {"trackers", "regression", "p2"},
            description = "WEIGHT_003 Switch kg and lbs")
    @Description("Pounds shows the weight × 2.20462 on the sheet and the tile; switching back restores kg")
    public void weight003_switchUnits() {
        HomePage home = home();
        WeightPage weight = home.openWeight();
        double kg = weight.current();
        try {
            weight.selectUnit("Pounds (lbs)");
            assertEquals(weight.unit(), "lbs");
            assertEquals(weight.current(), WeightPage.toLbs(kg), 0.001, "CURRENT in pounds");
            home = weight.done();
            assertTrue(home.tileLabel("WEIGHT").startsWith("WEIGHT, " + kgOrLbs(WeightPage.toLbs(kg)) + ", lbs"),
                    "tile in pounds, was: " + home.tileLabel("WEIGHT"));
        } finally {
            // The unit is a saved setting: always put it back for the next test.
            home = home();
            home.open();
            WeightPage back = home.openWeight();
            if (!"kg".equals(back.unit())) {
                back.selectUnit("Kilograms (kg)");
            }
            assertEquals(back.current(), kg, 0.001, "back in kilograms");
            home = back.done();
        }
        assertTrue(home.tileLabel("WEIGHT").startsWith("WEIGHT, " + kgOrLbs(kg) + ", kg"),
                "tile back in kilograms, was: " + home.tileLabel("WEIGHT"));
    }

    // ---------------------------------------------------------------- mood

    @Test(groups = {"trackers", "regression", "p1"},
            description = "MOOD_001 Log mood and energy")
    @Description("Choosing Good and High and logging shows 'Logged!'; the MOOD tile then shows Good")
    public void mood001_logMoodAndEnergy() {
        MoodPage mood = home().openMood().choose("Good").choose("High").log();
        HomePage home = mood.done();

        String tile = home.tileLabel("MOOD");
        assertTrue(tile.contains("Good"), "MOOD tile shows Good, was: " + tile);
    }

    @Test(groups = {"trackers", "regression", "p2"},
            description = "MOOD_002 The latest mood wins")
    @Description("Logging Great then Bad adds two Recent entries and the tile shows Bad")
    public void mood002_latestMoodWins() {
        LocalDate today = home().deviceToday();
        MoodPage mood = home().openMood();
        int recentBefore = mood.recentEntriesOn(today);

        mood.choose("Great").log();
        mood.choose("Bad").log();
        assertEquals(mood.recentEntriesOn(today), recentBefore + 2, "two more Recent entries today");

        String tile = mood.done().tileLabel("MOOD");
        assertTrue(tile.contains("Bad") && !tile.contains("Great"), "tile shows the latest mood, was: " + tile);
    }

    @Test(groups = {"trackers", "regression", "p2"},
            description = "MOOD_003 Done without logging changes nothing")
    @Description("Picking a mood and closing with Done leaves the MOOD tile as it was")
    public void mood003_doneWithoutLoggingChangesNothing() {
        HomePage home = home();
        String tileBefore = home.tileLabel("MOOD");

        home = home.openMood().choose("Terrible").done();
        assertEquals(home.tileLabel("MOOD"), tileBefore, "tile unchanged after Done");
    }
}
