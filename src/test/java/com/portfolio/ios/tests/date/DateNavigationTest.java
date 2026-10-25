package com.portfolio.ios.tests.date;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.pages.HomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 4 — past days on Home: the week strip, the past-day banner, the
 * calendar, and logging to a previous date.
 *
 * <p><b>Simulator-only.</b> Home lets you pick days from the install date
 * ({@code UserProfile.createdAt}) to today, so a freshly onboarded app has no
 * past day. After the class's one onboarding the install date is moved back
 * {@value #INSTALL_DAYS_AGO} days in the app's own database, which is only
 * reachable on the simulator. On a real device the class skips before
 * onboarding.
 *
 * <p>The selected day is not exposed to accessibility, so tests verify it
 * through the header title ("Yesterday, Oct 4" / "Oct 3") and the banner.
 */
@Epic("Dates")
@Feature("Past days & calendar")
public class DateNavigationTest extends AbstractAppTest {

    static final int INSTALL_DAYS_AGO = 3;

    @Override
    protected String simulatorOnlyReason() {
        return "past days need a backdated install date, which can only be set in the simulator's app database";
    }

    @BeforeClass(alwaysRun = true)
    public void backdateInstall() {
        if (com.portfolio.ios.core.Config.isReal()) {
            return;   // the class is skipped on a device (simulatorOnlyReason)
        }
        if (!AppStateResetter.backdateInstall(INSTALL_DAYS_AGO)) {
            throw new IllegalStateException("Could not move the install date back " + INSTALL_DAYS_AGO + " days");
        }
        relaunchApp();
    }

    private LocalDate today() {
        return home().deviceToday();
    }

    /** {@code date}'s day number, or a skip when the calendar opened on today's month won't show it. */
    private static int sameMonthDay(LocalDate date, LocalDate today, String why) {
        if (date.getMonthValue() != today.getMonthValue()) {
            throw new SkipException(why + " (" + date + ") is in a different month from today; "
                    + "the calendar opens on today's month");
        }
        return date.getDayOfMonth();
    }

    @Test(groups = {"date", "regression", "p1"},
            description = "DATE_001 Yesterday and back to today")
    @Description("Selecting yesterday shows 'Yesterday, <date>', the EDITING PAST DAY banner and "
            + "'Yesterday's Meals'; Today returns to today")
    public void date001_yesterdayAndBackToToday() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);

        home.tapDayPill(yesterday);
        assertTrue(home.showsTitle(HomePage.titleFor(yesterday, today)), "header for yesterday");
        assertTrue(home.isPastDayBannerShowing(), "EDITING PAST DAY banner");
        assertTrue(home.bannerShowsDate(yesterday), "banner names yesterday");
        assertTrue(home.showsMealsHeading("Yesterday's Meals"), "meals heading for yesterday");

        home.scrollToTop();
        home.backToToday();
        assertTrue(home.showsTitle(HomePage.todayTitle(today)), "header back on today");
        assertFalse(home.isPastDayBannerShowing(), "banner gone on today");
    }

    @Test(groups = {"date", "smoke", "regression", "p1"},
            description = "DATE_002 Log food to yesterday")
    @Description("On yesterday the + menu hides Workout and Meal Plan and asks 'Log for a previous date?'; "
            + "Yes logs Banana to yesterday only")
    public void date002_logFoodToYesterday() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);
        int remainingTodayBefore = home.remainingKcal();

        home.tapDayPill(yesterday);
        assertTrue(home.showsTitle(HomePage.titleFor(yesterday, today)), "on yesterday");
        int remainingYesterdayBefore = home.remainingKcal();

        home.openCreateMenu();
        assertTrue(home.createMenuOffers("Search"), "Search offered on a past day");
        assertFalse(home.createMenuOffers("Workout"), "Workout hidden on a past day");
        assertFalse(home.createMenuOffers("Meal Plan"), "Meal Plan hidden on a past day");

        home.chooseCreateItemOnPastDay("Search");
        assertTrue(home.previousDatePromptShowsDate(yesterday), "prompt names yesterday");
        home = home.answerPreviousDatePrompt("Yes")
                .search("banana").open("Banana").selectMeal("Breakfast").log();

        assertTrue(home.showsTitle(HomePage.titleFor(yesterday, today)), "still on yesterday after logging");
        assertEquals(home.remainingKcal(), remainingYesterdayBefore - 105, "yesterday's Remaining");
        assertTrue(home.mealCardContains("BREAKFAST", "Banana"), "Banana in yesterday's BREAKFAST card");

        home.backToToday();
        assertEquals(home.remainingKcal(), remainingTodayBefore, "today's Remaining unchanged");
        assertFalse(home.hasLoggedFood("Banana"), "Banana not logged today");
    }

    @Test(groups = {"date", "regression", "p1"},
            description = "DATE_003 Log for Today from a past day")
    @Description("On a past day, 'Log for Today' switches to today and logs Apple there, not on the past day")
    public void date003_logForTodayFromPastDay() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate twoDaysAgo = today.minusDays(2);

        home.tapDayPill(twoDaysAgo);
        assertTrue(home.showsTitle(HomePage.titleFor(twoDaysAgo, today)), "on two days ago");

        home.openCreateMenu();
        home.chooseCreateItemOnPastDay("Search");
        home = home.answerPreviousDatePrompt("Log for Today")
                .search("apple").open("Apple").selectMeal("Snack").log();

        assertTrue(home.showsTitle(HomePage.todayTitle(today)), "switched to today");
        assertTrue(home.mealCardContains("SNACK", "Apple"), "Apple logged today");

        home.tapDayPill(twoDaysAgo);
        assertTrue(home.showsTitle(HomePage.titleFor(twoDaysAgo, today)), "back on two days ago");
        assertFalse(home.hasLoggedFood("Apple"), "Apple not logged on the past day");
    }

    @Test(groups = {"date", "regression", "p1"},
            description = "DATE_004 Calendar Done selects a day")
    @Description("The calendar names the first selectable day; picking two days ago and Done shows that day")
    public void date004_calendarDoneSelectsDay() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate twoDaysAgo = today.minusDays(2);
        int day = sameMonthDay(twoDaysAgo, today, "two days ago");

        home.openCalendar();
        assertTrue(home.calendarFooterNames(today.minusDays(INSTALL_DAYS_AGO)), "footer names the install day");
        home.pickCalendarDay(day).confirmCalendar();

        assertTrue(home.showsTitle(HomePage.titleFor(twoDaysAgo, today)), "header shows the picked day");
        assertTrue(home.isPastDayBannerShowing(), "past-day banner shown");
    }

    @Test(groups = {"date", "regression", "p2"},
            description = "DATE_005 Calendar Cancel keeps the selected day")
    @Description("With yesterday selected, picking another day and cancelling keeps yesterday")
    public void date005_calendarCancelKeepsSelection() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);
        int otherDay = sameMonthDay(today.minusDays(2), today, "two days ago");

        home.tapDayPill(yesterday);
        home.scrollToTop();
        home.openCalendar();
        home.pickCalendarDay(otherDay).cancelCalendar();

        assertTrue(home.showsTitle(HomePage.titleFor(yesterday, today)), "Cancel keeps yesterday");
    }

    @Test(groups = {"date", "regression", "p3"},
            description = "DATE_006 Calendar disables days outside the range")
    @Description("Tomorrow and the day before install are disabled in the calendar (checked when in "
            + "the month shown)")
    public void date006_calendarDisablesOutOfRangeDays() {
        HomePage home = home();
        LocalDate today = today();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate beforeInstall = today.minusDays(INSTALL_DAYS_AGO + 1);

        home.openCalendar();
        int checked = 0;
        if (tomorrow.getMonthValue() == today.getMonthValue()) {
            assertEquals(home.isCalendarDayEnabled(tomorrow.getDayOfMonth()), Boolean.FALSE, "tomorrow disabled");
            checked++;
        }
        if (beforeInstall.getMonthValue() == today.getMonthValue()) {
            assertEquals(home.isCalendarDayEnabled(beforeInstall.getDayOfMonth()), Boolean.FALSE,
                    "day before install disabled");
            checked++;
        }
        assertEquals(home.isCalendarDayEnabled(today.getDayOfMonth()), Boolean.TRUE, "today enabled");
        home.cancelCalendar();
        if (checked == 0) {
            throw new SkipException("tomorrow and the day before install are both outside today's month");
        }
    }

    @Test(groups = {"date", "regression", "p2"},
            description = "DATE_007 Weight and mood are hidden on a past day without entries")
    @Description("Today shows the Weight and Mood tiles; yesterday, with nothing logged, shows neither")
    public void date007_weightAndMoodHiddenOnEmptyPastDay() {
        HomePage home = home();
        LocalDate today = today();
        assertTrue(home.hasTile("WEIGHT"), "Weight tile today");
        assertTrue(home.hasTile("MOOD"), "Mood tile today");

        home.tapDayPill(today.minusDays(1));
        assertTrue(home.showsTitle(HomePage.titleFor(today.minusDays(1), today)), "on yesterday");
        assertFalse(home.hasTile("WEIGHT"), "no Weight tile on a past day without an entry");
        assertFalse(home.hasTile("MOOD"), "no Mood tile on a past day without an entry");
    }
}
