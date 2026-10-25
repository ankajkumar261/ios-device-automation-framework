package com.portfolio.ios.tests.activities;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.pages.ActivityCatalogSheet;
import com.portfolio.ios.pages.ActivitySheet;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.LiveWorkoutPage;
import com.portfolio.ios.pages.LogExercisePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Module 6 — Activities: logging exercise from the catalog or "Describe",
 * editing and deleting it on Log Exercise, and the live workout timer.
 *
 * <p>Calories are the app's own formula, kcal = MET × weight(kg) × hours,
 * with the onboarding weight. Sheets round the result; rows and Home
 * truncate it. The rounding gap is an open question, so durations here are
 * picked where both agree (Running 30 min at 70 kg is exactly 343).
 *
 * <p>Confirmed behaviour: logged exercise does not change Home's Remaining
 * (the app budgets activity through the activity level), and a live workout
 * of 10–59 s is saved as "0 min".
 */
@Epic("Activities")
@Feature("Exercise logging")
public class ActivitiesTest extends AbstractAppTest {

    private static final double RUNNING_MET = 9.8;
    private static final double YOGA_MET = 3.0;
    private static final double INTENSE_MET = 8.0;
    private static final double MODERATE_MET = 5.0;
    private static final Pattern BURNED = Pattern.compile("([0-9][0-9,]*) kcal burned");

    /** kcal before rounding, as the app computes it for the onboarding weight. */
    private double burn(double met, int minutes) {
        return met * profile().weightKg() * minutes / 60.0;
    }

    /** What a sheet shows: rounded. */
    private static int sheetKcal(double kcal) {
        return (int) Math.round(kcal);
    }

    /** What rows and Home show: truncated. */
    private static int rowKcal(double kcal) {
        return (int) kcal;
    }

    @Override
    protected boolean restoreBaselineBeforeEachTest() {
        return true;
    }

    /**
     * Home with no activity logged today. Already true when the test starts
     * from the restored baseline; on a real device entries are removed
     * through the app's own Delete.
     */
    private HomePage cleanDay() {
        if (testStartsFromBaseline()) {
            return home();
        }
        HomePage home = home();
        if (home.latestActivity() != null) {
            home = home.openLogExercise().deleteAll().done();
        }
        assertNull(home.latestActivity(), "no activity left on Home");
        return home;
    }

    /** Logs {@code name} from Home's Activities "Add" at the sheet's default 30 min. */
    private HomePage logFromHome(String name) {
        HomePage home = home();
        ActivitySheet sheet = home.openActivityCatalog().search(name).pick(name);
        assertEquals(sheet.minutes(), 30, "default duration");
        sheet.add();
        return home.waitUntilLoaded();
    }

    private static int stepsTileBurned(String tile) {
        Matcher m = BURNED.matcher(tile);
        assertTrue(m.find(), "STEPS tile shows the burn, was: " + tile);
        return Integer.parseInt(m.group(1).replace(",", ""));
    }

    // ---------------------------------------------------------------- catalog

    @Test(groups = {"activities", "smoke", "regression", "p0"},
            description = "ACT_001 Log an activity from the catalog")
    @Description("Running at the default 30 min shows 343 kcal and is logged; the Activities card and the "
            + "STEPS tile show it; Home's Remaining is unchanged (confirmed behaviour)")
    public void act001_logFromCatalog() {
        HomePage home = cleanDay();
        home.scrollToTop();
        int remainingBefore = home.remainingKcal();

        ActivitySheet sheet = home.openActivityCatalog().search("Running").pick("Running");
        assertEquals(sheet.minutes(), 30, "default duration");
        assertEquals(sheet.kcal(), sheetKcal(burn(RUNNING_MET, 30)), "calories on the sheet");
        sheet.add();
        home.waitUntilLoaded();

        int logged = rowKcal(burn(RUNNING_MET, 30));
        assertEquals(home.latestActivity(), List.of("Running", "30 min · " + logged + " kcal"),
                "Activities card");

        LogExercisePage log = home.openLogExercise();
        int appleHealth = log.appleHealthKcal();
        home = log.done();
        assertEquals(stepsTileBurned(home.tileLabel("STEPS")), logged + appleHealth,
                "STEPS tile burn = logged exercise + Apple Health");

        home.scrollToTop();
        assertEquals(home.remainingKcal(), remainingBefore, "exercise does not change Remaining");
    }

    @Test(groups = {"activities", "regression", "p1"},
            description = "ACT_002 Duration stepper recalculates calories")
    @Description("+5 min and −10 min move the duration 1 min per tap and recalculate the calories; "
            + "Cancel logs nothing")
    public void act002_durationStepper() {
        HomePage home = cleanDay();
        ActivityCatalogSheet catalog = home.openActivityCatalog().search("Running");
        ActivitySheet sheet = catalog.pick("Running");

        sheet.increase(5);
        assertEquals(sheet.minutes(), 35);
        assertEquals(sheet.kcal(), sheetKcal(burn(RUNNING_MET, 35)), "35 min");
        sheet.decrease(10);
        assertEquals(sheet.minutes(), 25);
        assertEquals(sheet.kcal(), sheetKcal(burn(RUNNING_MET, 25)), "25 min");

        sheet.cancel();
        catalog.done();
        assertNull(home.latestActivity(), "Cancel logs nothing");
    }

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_003 Duration cannot go below 5 min")
    @Description("Tapping − stops at 5 min; Cancel logs nothing")
    public void act003_minimumDuration() {
        HomePage home = cleanDay();
        ActivityCatalogSheet catalog = home.openActivityCatalog().search("Running");
        ActivitySheet sheet = catalog.pick("Running");

        assertEquals(sheet.decreaseToMinimum(), 5, "lowest duration");
        assertEquals(sheet.kcal(), sheetKcal(burn(RUNNING_MET, 5)), "5 min");

        sheet.cancel();
        catalog.done();
        assertNull(home.latestActivity(), "Cancel logs nothing");
    }

    // ---------------------------------------------------------------- Log Exercise

    @Test(groups = {"activities", "regression", "p1"},
            description = "ACT_004 Log Exercise totals")
    @Description("Running 30 + Yoga 30 give 448 kcal and '2 entries'; rows newest first; "
            + "Today's Burn = logged exercise + Apple Health; Home shows the newest")
    public void act004_logExerciseTotals() {
        cleanDay();
        LogExercisePage log = logFromHome("Running").openLogExercise().log("Yoga", 30);

        int running = rowKcal(burn(RUNNING_MET, 30));
        int yoga = rowKcal(burn(YOGA_MET, 30));
        assertEquals(log.loggedExerciseKcal(), running + yoga, "Logged exercise");
        assertEquals(log.entriesCaption(), "2 entries");
        assertEquals(log.todaysBurn(), log.loggedExerciseKcal() + log.appleHealthKcal(), "Today's Burn");

        List<String> rows = log.rows();
        assertEquals(rows.size(), 2, "rows: " + rows);
        assertTrue(rows.get(0).startsWith("Yoga, 30 min · ") && rows.get(0).endsWith(", " + yoga + " kcal"),
                "newest first: " + rows);
        assertTrue(rows.get(1).startsWith("Running, 30 min · ") && rows.get(1).endsWith(", " + running + " kcal"),
                "older second: " + rows);

        HomePage home = log.done();
        assertEquals(home.latestActivity(), List.of("Yoga", "30 min · " + yoga + " kcal"),
                "Home shows the newest activity");
    }

    @Test(groups = {"activities", "regression", "p1"},
            description = "ACT_005 Edit an activity's duration")
    @Description("Save is disabled until the duration changes; 30 → 40 min rescales the calories from the "
            + "entry's own rate; the row, the total and Home update")
    public void act005_editDuration() {
        cleanDay();
        LogExercisePage log = logFromHome("Running").openLogExercise();

        ActivitySheet edit = log.openRow("Running");
        assertFalse(edit.isSaveEnabled(), "Save disabled before any change");
        assertEquals(edit.minutes(), 30);
        assertEquals(edit.kcal(), sheetKcal(burn(RUNNING_MET, 30)));

        edit.increase(10);
        int edited = sheetKcal(burn(RUNNING_MET, 30) / 30 * 40);
        assertEquals(edit.kcal(), edited, "40 min at the entry's rate");
        assertTrue(edit.isSaveEnabled(), "Save enabled after a change");
        log = edit.save();

        String row = log.row("Running");
        assertNotNull(row, "Running row after saving");
        assertTrue(row.startsWith("Running, 40 min · ") && row.endsWith(", " + edited + " kcal"), "row: " + row);
        assertEquals(log.loggedExerciseKcal(), edited, "Logged exercise");

        HomePage home = log.done();
        assertEquals(home.latestActivity(), List.of("Running", "40 min · " + edited + " kcal"), "Home card");
    }

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_006 Cancel an edit")
    @Description("Changing the duration and tapping Cancel leaves the entry as it was")
    public void act006_cancelEdit() {
        cleanDay();
        LogExercisePage log = logFromHome("Running").openLogExercise();
        String rowBefore = log.row("Running");
        int totalBefore = log.loggedExerciseKcal();

        ActivitySheet edit = log.openRow("Running").increase(5);
        edit.cancel();
        log.waitUntilLoaded();

        assertEquals(log.row("Running"), rowBefore, "row unchanged");
        assertEquals(log.loggedExerciseKcal(), totalBefore, "total unchanged");
    }

    @Test(groups = {"activities", "regression", "p1"},
            description = "ACT_007 Delete activities")
    @Description("Delete asks 'Delete activity?'; Cancel keeps the entry, Delete removes it; with nothing left "
            + "Log Exercise says 'Nothing logged yet' and the Home card shows only Add")
    public void act007_deleteActivities() {
        cleanDay();
        LogExercisePage log = logFromHome("Running").openLogExercise().log("Yoga", 30);

        ActivitySheet edit = log.openRow("Running").tapDelete();
        assertTrue(edit.deleteAlertSays("This can't be undone."), "alert message");
        edit.cancelDelete().cancel();
        log.waitUntilLoaded();
        assertNotNull(log.row("Running"), "Cancel keeps the entry");
        assertEquals(log.entriesCaption(), "2 entries");

        log = log.openRow("Running").tapDelete().confirmDelete();
        assertNull(log.row("Running"), "Running deleted");
        assertEquals(log.entriesCaption(), "1 entry");
        assertEquals(log.loggedExerciseKcal(), rowKcal(burn(YOGA_MET, 30)), "only Yoga left");

        log = log.openRow("Yoga").tapDelete().confirmDelete();
        assertTrue(log.rows().isEmpty(), "no rows left: " + log.rows());
        assertEquals(log.entriesCaption(), "Nothing logged yet");
        assertEquals(log.loggedExerciseKcal(), 0);

        HomePage home = log.done();
        assertNull(home.latestActivity(), "Home card is header-only again");
    }

    // ---------------------------------------------------------------- Describe and search

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_008 Describe a custom workout")
    @Description("Add stays disabled for an empty or blank name; 'Evening Stretch', 30 min, Intense is "
            + "logged with 280 kcal")
    public void act008_describeWorkout() {
        HomePage home = cleanDay();
        ActivitySheet describe = home.openActivityCatalog().describe();
        assertFalse(describe.isAddEnabled(), "Add disabled without a name");
        assertEquals(describe.kcal(), sheetKcal(burn(MODERATE_MET, 30)), "default: Moderate, 30 min");

        describe.enterName("   ");
        assertFalse(describe.isAddEnabled(), "Add disabled for a blank name");

        describe.enterName("Evening Stretch");
        assertTrue(describe.isAddEnabled(), "Add enabled with a name");
        int intense = sheetKcal(burn(INTENSE_MET, 30));
        describe.chooseIntensity("Intense", intense);
        assertEquals(describe.minutes(), 30);
        describe.add();
        home.waitUntilLoaded();

        assertEquals(home.latestActivity(), List.of("Evening Stretch", "30 min · " + intense + " kcal"),
                "custom workout on Home");
    }

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_009 Search activities and Recent")
    @Description("'run' lists Running, Running (Fast), Treadmill Running; 'zzzz' lists nothing; clearing "
            + "brings back Describe; after a log, Recent shows that activity")
    public void act009_searchAndRecent() {
        HomePage home = cleanDay();
        ActivityCatalogSheet catalog = home.openActivityCatalog();
        assertTrue(catalog.recent().isEmpty(), "no Recent with nothing logged: " + catalog.recent());

        catalog.search("run");
        assertEquals(Set.copyOf(catalog.listedActivities()),
                Set.of("Running", "Running (Fast)", "Treadmill Running"), "matches for 'run'");

        catalog.clearSearch().search("zzzz");
        assertTrue(catalog.listedActivities().isEmpty(), "no matches: " + catalog.listedActivities());

        catalog.clearSearch();
        assertTrue(catalog.hasDescribeRow(), "Describe back after clearing");
        catalog.search("Running").pick("Running").add();
        home.waitUntilLoaded();

        catalog = home.openActivityCatalog();
        assertEquals(catalog.recent(), List.of("Running"), "Recent after logging Running");
        catalog.done();
    }

    // ---------------------------------------------------------------- live workout

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_010 Live workout under 10 s cannot be saved")
    @Description("Ending within 10 s explains it is too short and offers only Discard / Keep going; "
            + "Discard logs nothing")
    public void act010_liveWorkoutTooShort() {
        HomePage home = cleanDay();
        LiveWorkoutPage live = home.openLiveWorkout();
        try {
            live.chooseType("Running").start().end();
            assertTrue(live.endAlertSays(LiveWorkoutPage.TOO_SHORT), "too-short message");
            assertEquals(Set.copyOf(live.endAlertButtons()), Set.of("Discard", "Keep going"), "alert buttons");
            live.discard();
        } finally {
            live.abandon();
        }
        home.waitUntilLoaded();
        assertNull(home.latestActivity(), "Discard logs nothing");
    }

    @Test(groups = {"activities", "regression", "p2"},
            description = "ACT_011 Live workout of 10 s is saved as 0 min")
    @Description("After 10 s on the clock, ending offers Save workout ('under a minute'); the entry is saved "
            + "as Running, 0 min (confirmed behaviour), with the calories of the time it ran")
    public void act011_liveWorkoutUnderAMinute() {
        HomePage home = cleanDay();
        LiveWorkoutPage live = home.openLiveWorkout();
        long startedAt;
        long savedAt;
        try {
            live.chooseType("Running");
            startedAt = System.nanoTime();
            live.start().waitForClock(10).end();
            assertTrue(live.endAlertSays(LiveWorkoutPage.UNDER_A_MINUTE), "under-a-minute message");
            assertTrue(live.endAlertButtons().contains("Save workout"), "Save offered: " + live.endAlertButtons());
            live.saveWorkout();
            savedAt = System.nanoTime();
        } finally {
            live.abandon();
        }
        home.waitUntilLoaded();

        List<String> latest = home.latestActivity();
        assertNotNull(latest, "saved workout on Home");
        assertEquals(latest.get(0), "Running");
        assertTrue(latest.get(1).startsWith("0 min · "), "saved as 0 min: " + latest);
        int kcal = Integer.parseInt(latest.get(1).replaceAll(".*· ([0-9]+) kcal", "$1"));
        double ranSeconds = (savedAt - startedAt) / 1e9;
        assertTrue(kcal >= rowKcal(RUNNING_MET * profile().weightKg() * 10 / 3600.0)
                        && kcal <= rowKcal(RUNNING_MET * profile().weightKg() * ranSeconds / 3600.0),
                "calories for 10–" + Math.round(ranSeconds) + " s of running, was " + kcal);
    }
}
