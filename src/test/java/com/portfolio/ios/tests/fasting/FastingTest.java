package com.portfolio.ios.tests.fasting;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.pages.BasePage;
import com.portfolio.ios.pages.FastingCardPage;
import com.portfolio.ios.pages.FastingEndAlert;
import com.portfolio.ios.pages.FastingHubPage;
import com.portfolio.ios.pages.FastingHubPage.HistoryRow;
import com.portfolio.ios.pages.FastingSetupPage;
import com.portfolio.ios.pages.FastingText;
import com.portfolio.ios.pages.HomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 7 — Intermittent fasting: first setup, starting and ending fasts,
 * the hub (plan, history), custom protocols, editing and deleting.
 *
 * <p>Every test starts with no plan, no running fast and no history, cleared
 * through the app's own controls. Expected times come from the times the app
 * shows (the schedule's start defaults to "now"), so the tests hold at any
 * time of day. FAST_010/011 move a fast into the past in the app's database,
 * so they run on the simulator only.
 *
 * <p>Confirmed behaviour: deleting the plan opens the protocol picker.
 */
@Epic("Fasting")
@Feature("Intermittent fasting")
public class FastingTest extends AbstractAppTest {

    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US);

    // ---------------------------------------------------------------- helpers

    @Override
    protected boolean restoreBaselineBeforeEachTest() {
        return true;
    }

    /**
     * No plan, no running fast, no history; back on Home. Already true when
     * the test starts from the restored baseline; on a real device it is done
     * through the app.
     */
    private HomePage resetFasting() {
        if (testStartsFromBaseline()) {
            return home();
        }
        for (int round = 0; round < 5; round++) {
            BasePage screen = home().openFasting();
            if (screen instanceof FastingSetupPage setup) {
                setup.close();   // nothing saved: setup is what a first-time user sees
                return home().open();
            }
            FastingHubPage hub = (FastingHubPage) screen;
            if (hub.hasCurrentFast()) {
                FastingEndAlert alert = hub.tapEndFast();
                alert.choose(FastingEndAlert.SHORT_TITLE.equals(alert.title()) ? "End & Discard" : "End Fast");
            }
            while (!hub.history().isEmpty()) {
                hub.deleteNewestFast();
            }
            if (hub.hasPlan()) {
                hub.tapDeletePlan().confirmDeletePlan();
            }
            home().open();
        }
        throw new AssertionError("Fasting state could not be cleared");
    }

    /** From a reset Home: setup (through the introduction on first use) to the protocol picker. */
    private FastingSetupPage protocolPicker(HomePage home) {
        FastingSetupPage setup = home.openFastingSetup();
        return setup.isShowingEducation() ? setup.completeEducation() : setup.waitForProtocolPicker();
    }

    /** Starts a 16:8 fast now with the default "Every day"; returns its start time. */
    private LocalTime startSixteenEight(HomePage home) {
        FastingSetupPage setup = protocolPicker(home).choosePreset("16:8").continueToSchedule();
        assertEquals(setup.scheduleButtonLabel(), "Start Fast", "start time defaults to now");
        LocalTime start = setup.fastWindow()[0];
        setup.save();
        return start;
    }

    private static String stamp(LocalDate day, LocalTime time) {
        return FastingText.dayAndTime(LocalDateTime.of(day, time));
    }

    // ---------------------------------------------------------------- start a fast

    @Test(groups = {"fasting", "smoke", "regression", "p0"},
            description = "FAST_001 First setup starts a 16:8 fast")
    @Description("Introduction → 6 protocol cards → 16:8 → the preview ends 16 h after the start → Start Fast; "
            + "Home shows the running fast with matching Started / End and a counting clock")
    public void fast001_firstSetupStartsFast() {
        HomePage home = resetFasting();
        LocalDate today = home.deviceToday();
        FastingSetupPage setup = protocolPicker(home);

        List<String> cards = setup.protocolCards();
        assertEquals(cards.size(), 6, "five presets + custom: " + cards);
        for (String title : List.of("16:8 - Classic", "12:12 - Beginner", "14:10 - Balanced",
                "18:6 - Advanced", "20:4 - Very Advanced")) {
            assertTrue(cards.stream().anyMatch(card -> card.contains(", Hours, " + title + ", ")),
                    "card " + title + " in " + cards);
        }
        assertTrue(cards.stream().anyMatch(card -> card.startsWith("Add Custom")), "Add Custom card");

        setup.choosePreset("16:8").continueToSchedule();
        LocalTime[] fast = setup.fastWindow();
        LocalTime[] eat = setup.eatWindow();
        assertEquals(fast[1], fast[0].plusHours(16), "fast window is 16 h");
        assertEquals(eat[0], fast[1], "eating starts when the fast ends");
        assertEquals(eat[1], fast[0], "eating ends when the next fast starts");
        assertEquals(setup.scheduleButtonLabel(), "Start Fast");
        home = setup.save();

        FastingCardPage card = home.fastingCard();
        assertEquals(card.title(), "Fasting in progress");
        assertEquals(card.protocolLabel(), "16:8 protocol");
        LocalDateTime start = LocalDateTime.of(today, fast[0]);
        assertEquals(card.stamps(), List.of(FastingText.dayAndTime(start),
                FastingText.dayAndTime(start.plusHours(16))), "Started / End");
        int clock = card.elapsedSeconds();
        Waits.untilTrue(Waits.SHORT, "elapsed clock to move",
                () -> card.elapsedSeconds() > clock);
    }

    // ---------------------------------------------------------------- end a fast

    @Test(groups = {"fasting", "regression", "p1"},
            description = "FAST_002 End a short fast and save it")
    @Description("Under 4 h, End Fast asks 'End this short fast?' with End & Save / End & Discard / Keep Fasting; "
            + "Keep Fasting keeps it running; End & Save records it as 'Ended early'; Dismiss clears the card")
    public void fast002_endShortFastAndSave() {
        HomePage home = resetFasting();
        LocalDate today = home.deviceToday();
        LocalTime start = startSixteenEight(home);
        FastingCardPage card = home.fastingCard();

        FastingEndAlert alert = card.tapEndFast();
        assertEquals(alert.title(), FastingEndAlert.SHORT_TITLE);
        assertTrue(alert.message().startsWith("You've fasted ")
                        && alert.message().endsWith(", under the 4-hour minimum. Save it to your history, or discard it?"),
                "message: " + alert.message());
        assertEquals(Set.copyOf(alert.buttons()), Set.of("End & Save", "End & Discard", "Keep Fasting"));
        alert.choose("Keep Fasting");
        assertEquals(card.title(), "Fasting in progress", "Keep Fasting keeps it running");

        card.tapEndFast().choose("End & Save");
        assertEquals(card.title(), "Fast ended");
        assertEquals(card.protocolLabel(), "16:8 protocol");
        assertEquals(card.stamps().get(0), stamp(today, start), "Started");
        String length = card.length();
        assertTrue(FastingText.minutes(length) < 240, "a short fast: " + length);

        FastingHubPage hub = home.openFastingHub();
        assertFalse(hub.hasCurrentFast(), "no fast running");
        assertEquals(hub.history(), List.of(new HistoryRow(FastingText.historyDate(today), length, "Ended early")),
                "history");
        assertTrue(hub.hasPlan(), "the plan stays");
        home = hub.done();

        home = card.dismiss();
        assertFalse(home.fastingCard().isShowing(), "card dismissed");
    }

    @Test(groups = {"fasting", "regression", "p1"},
            description = "FAST_003 End a short fast and discard it")
    @Description("End & Discard keeps nothing: no history, no card, and today's fast does not restart")
    public void fast003_endShortFastAndDiscard() {
        HomePage home = resetFasting();
        startSixteenEight(home);

        home.fastingCard().tapEndFast().choose("End & Discard");
        assertFalse(home.fastingCard().isShowing(), "no fasting card");

        FastingHubPage hub = home.openFastingHub();
        assertFalse(hub.hasCurrentFast(), "no fast running");
        assertTrue(hub.historyIsEmpty(), "nothing in history: " + hub.history());
        home = hub.done();

        hub = home.openFastingHub();
        assertFalse(hub.hasCurrentFast(), "today's discarded fast does not restart");
        hub.done();
    }

    // ---------------------------------------------------------------- hub and plans

    @Test(groups = {"fasting", "regression", "p1"},
            description = "FAST_004 Hub shows the plan")
    @Description("With a plan the tile opens the hub: protocol, window, 'Every day', next fast tomorrow at the "
            + "start time, Update / Delete, 'Edit Fast Schedule'; Update skips the introduction")
    public void fast004_hubShowsPlan() {
        HomePage home = resetFasting();
        LocalDate today = home.deviceToday();
        LocalTime start = startSixteenEight(home);

        FastingHubPage hub = home.openFastingHub();
        assertTrue(hub.hasCurrentFast(), "'Current Fast' while it runs");
        assertEquals(hub.planProtocol(), "16:8");
        assertEquals(hub.planWindow(), "Fast " + FastingText.time(start) + " → "
                + FastingText.time(start.plusHours(16)));
        assertEquals(hub.planRepeat(), "Every day");
        assertEquals(hub.planNextFast(), "Next fast " + stamp(today.plusDays(1), start));
        assertEquals(hub.bottomButton(), "Edit Fast Schedule");

        FastingSetupPage setup = hub.update();
        assertFalse(setup.isShowingEducation(), "no introduction the second time");
        setup.close();
        hub.waitUntilLoaded().done();
    }

    @Test(groups = {"fasting", "regression", "p1"},
            description = "FAST_005 Schedule a fast for another day")
    @Description("Selected days needs a day; with only tomorrow picked the button reads 'Schedule Fast', no fast "
            + "starts, and the hub shows tomorrow as the next fast")
    public void fast005_scheduleForAnotherDay() {
        HomePage home = resetFasting();
        LocalDate tomorrow = home.deviceToday().plusDays(1);
        FastingSetupPage setup = protocolPicker(home).choosePreset("16:8").continueToSchedule();

        setup.chooseRepeat("Selected days");
        assertTrue(setup.showsSelectOneDay(), "'Select at least one day'");
        assertEquals(setup.scheduleButtonLabel(), "Schedule Fast");
        assertFalse(setup.isScheduleButtonEnabled(), "disabled without a day");

        setup.toggleWeekday(tomorrow.getDayOfWeek());
        assertFalse(setup.showsSelectOneDay(), "hint gone with a day picked");
        assertTrue(setup.isScheduleButtonEnabled(), "enabled with a day");
        assertEquals(setup.scheduleButtonLabel(), "Schedule Fast", "today is not a fasting day");
        LocalTime start = setup.fastWindow()[0];
        home = setup.save();
        assertFalse(home.fastingCard().isShowing(), "no fast running");

        FastingHubPage hub = home.openFastingHub();
        assertFalse(hub.hasCurrentFast());
        assertEquals(hub.planRepeat(), WEEKDAY.format(tomorrow), "repeat days");
        assertEquals(hub.planNextFast(), "Next fast " + stamp(tomorrow, start));
        hub.done();
    }

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_006 Custom protocol")
    @Description("Custom hours stop at 23 and 4; a 6-hour custom plan previews start + 6 h and runs as '6h protocol'")
    public void fast006_customProtocol() {
        HomePage home = resetFasting();
        LocalDate today = home.deviceToday();
        FastingSetupPage setup = protocolPicker(home).openCustom();

        assertEquals(setup.customHours(), 16, "default");
        assertEquals(setup.customStepToLimit("plus"), 23, "upper limit");
        assertFalse(setup.isCustomStepEnabled("plus"), "+ off at 23");
        assertEquals(setup.customStepToLimit("minus"), 4, "lower limit");
        assertFalse(setup.isCustomStepEnabled("minus"), "− off at 4");
        setup.setCustomHours(6).confirmCustom();
        assertTrue(setup.customCardLabel().startsWith("6, Hours, Custom Plan, "), setup.customCardLabel());

        setup.continueToSchedule();
        LocalTime[] fast = setup.fastWindow();
        assertEquals(fast[1], fast[0].plusHours(6), "6-hour window");
        home = setup.save();

        FastingCardPage card = home.fastingCard();
        assertEquals(card.protocolLabel(), "6h protocol");
        LocalDateTime start = LocalDateTime.of(today, fast[0]);
        assertEquals(card.stamps(), List.of(FastingText.dayAndTime(start),
                FastingText.dayAndTime(start.plusHours(6))), "Started / End");
    }

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_007 Update the plan")
    @Description("Update keeps the start time and days; switching 16:8 → 14:10 shows a 14-hour window on the hub")
    public void fast007_updatePlan() {
        HomePage home = resetFasting();
        LocalDate tomorrow = home.deviceToday().plusDays(1);
        FastingSetupPage setup = protocolPicker(home).choosePreset("16:8").continueToSchedule()
                .chooseRepeat("Selected days").toggleWeekday(tomorrow.getDayOfWeek());
        LocalTime start = setup.fastWindow()[0];
        home = setup.save();

        setup = home.openFastingHub().update().choosePreset("14:10").continueToSchedule();
        LocalTime[] fast = setup.fastWindow();
        assertEquals(fast[0], start, "start time kept");
        assertEquals(fast[1], start.plusHours(14), "14-hour window");
        assertEquals(setup.scheduleButtonLabel(), "Schedule Fast", "days kept: still only tomorrow");
        home = setup.save();

        FastingHubPage hub = home.openFastingHub();
        assertEquals(hub.planProtocol(), "14:10");
        assertEquals(hub.planWindow(), "Fast " + FastingText.time(start) + " → "
                + FastingText.time(start.plusHours(14)));
        assertEquals(hub.planRepeat(), WEEKDAY.format(tomorrow));
        hub.done();
    }

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_008 Delete a fast from history")
    @Description("Long-press → Delete Fast → confirm removes the fast; the deleted day does not restart")
    public void fast008_deleteFastFromHistory() {
        HomePage home = resetFasting();
        startSixteenEight(home);
        home.fastingCard().tapEndFast().choose("End & Save");

        FastingHubPage hub = home.openFastingHub();
        assertEquals(hub.history().size(), 1, "one fast in history");
        hub.deleteNewestFast();
        assertTrue(hub.historyIsEmpty(), "history empty: " + hub.history());
        assertFalse(hub.hasCurrentFast(), "the deleted day does not restart");
        home = hub.done();
        assertFalse(home.fastingCard().isShowing(), "no card for a deleted fast");
    }

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_009 Delete the plan")
    @Description("Tapping outside the confirmation keeps the plan; confirming deletes it and opens the protocol "
            + "picker (confirmed behaviour); past fasts stay and the hub offers 'Start New Fast'")
    public void fast009_deletePlan() {
        HomePage home = resetFasting();
        startSixteenEight(home);
        home.fastingCard().tapEndFast().choose("End & Save");

        FastingHubPage hub = home.openFastingHub();
        hub.tapDeletePlan().dismissDeletePlan();
        assertTrue(hub.hasPlan(), "dismissing keeps the plan");

        FastingSetupPage setup = hub.tapDeletePlan().confirmDeletePlan();
        setup.close();
        hub.waitUntilLoaded();
        assertFalse(hub.hasPlan(), "plan deleted");
        assertEquals(hub.history().size(), 1, "past fasts stay");
        assertEquals(hub.bottomButton(), "Start New Fast");
        hub.done();
    }

    // ---------------------------------------------------------------- long fasts (simulator only)

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_010 A fast that reaches its goal completes")
    @Description("A 'Just today' 16:8 fast moved 17 h into the past completes at its goal on relaunch: "
            + "'Fast complete!', 16h 0m, 'Completed' in history, and the one-day plan is gone")
    public void fast010_fastReachesGoal() {
        if (Config.isReal()) {
            throw new SkipException("Simulator-only: moves the fast in the app's database");
        }
        HomePage home = resetFasting();
        FastingSetupPage setup = protocolPicker(home).choosePreset("16:8").continueToSchedule()
                .chooseRepeat("Just today");
        assertEquals(setup.scheduleButtonLabel(), "Start Fast");
        setup.save();

        assertTrue(AppStateResetter.shiftActiveFastBack(17), "moved the running fast 17 h back");
        home = relaunchApp();

        FastingCardPage card = home.fastingCard();
        assertEquals(card.title(), "Fast complete!");
        assertEquals(card.protocolLabel(), "16:8 protocol");
        assertEquals(card.length(), "16h 0m");

        // New UI (1.0.15): the fixed Completed banner overlaps the invite tile, so its
        // centre tap hits the banner's sheet instead of the hub. Dismissing the banner
        // first uncovers the tile; history stays (verified below).
        home = card.dismiss();
        assertFalse(home.fastingCard().isShowing(), "banner dismissed");

        FastingHubPage hub = home.openFastingHub();
        List<HistoryRow> history = hub.history();
        assertEquals(history.size(), 1, "history: " + history);
        assertEquals(history.get(0).status(), "Completed");
        // New UI (1.0.15): the hub shows the precise minutes (seconds-truncated, e.g. "15h 59m")
        // while the sheet rounds to "16 hours". Accept goal ±1 min for start/end seconds.
        int completedMinutes = FastingText.minutes(history.get(0).length());
        assertTrue(completedMinutes >= 959 && completedMinutes <= 960,
                "completed at goal (~16h): " + history.get(0).length());
        assertFalse(hub.hasPlan(), "a 'Just today' plan retires after its fast");
        assertEquals(hub.bottomButton(), "Start New Fast");
        hub.done();
    }

    @Test(groups = {"fasting", "regression", "p2"},
            description = "FAST_011 End a fast after 4 hours")
    @Description("A fast moved 5 h into the past asks 'End this fast?' (incomplete before the 16-hour goal) with "
            + "End Fast / Keep Fasting, and is recorded as 'Ended early' at about 5 h")
    public void fast011_endLongFast() {
        if (Config.isReal()) {
            throw new SkipException("Simulator-only: moves the fast in the app's database");
        }
        HomePage home = resetFasting();
        FastingSetupPage setup = protocolPicker(home).choosePreset("16:8").continueToSchedule()
                .chooseRepeat("Just today");
        setup.save();

        assertTrue(AppStateResetter.shiftActiveFastBack(5), "moved the running fast 5 h back");
        home = relaunchApp();

        FastingCardPage card = home.fastingCard();
        assertEquals(card.title(), "Fasting in progress");
        assertTrue(card.elapsedSeconds() >= 5 * 3600, "about 5 h in: " + card.elapsedSeconds() + " s");

        FastingEndAlert alert = card.tapEndFast();
        assertEquals(alert.title(), FastingEndAlert.LONG_TITLE);
        assertEquals(alert.message(),
                "You're ending before your 16-hour goal, so this fast will be recorded as incomplete.");
        assertEquals(Set.copyOf(alert.buttons()), Set.of("End Fast", "Keep Fasting"));
        alert.choose("End Fast");

        assertEquals(card.title(), "Fast ended");
        int minutes = FastingText.minutes(card.length());
        assertTrue(minutes >= 300 && minutes < 330, "about 5 h: " + card.length());

        // New UI (1.0.15): the fixed Ended banner overlaps the invite tile (no Upcoming
        // card for 'Just today'), so dismiss it first to reach the hub; history stays.
        home = card.dismiss();
        assertFalse(home.fastingCard().isShowing(), "banner dismissed");

        FastingHubPage hub = home.openFastingHub();
        assertEquals(hub.history().get(0).status(), "Ended early");
        hub.done();
    }
}
