package com.portfolio.ios.tests.insights;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.pages.CalorieDetailPage;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.InsightsPage;
import com.portfolio.ios.pages.InsightsPage.MealShare;
import com.portfolio.ios.pages.InsightsPage.Streak;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 10 — Insights and Calorie Detail, checked against food the test
 * logs through the app: 2 Bananas at breakfast and an Apple at lunch
 * (105 + 105 + 95 = 305 kcal).
 *
 * <p>INS_001 runs first, before anything is logged; the next tests share the
 * same three foods; INS_010 runs last and moves one more food 9 days back in
 * the app's database (simulator only) to give the app a "last week".
 *
 * <p>Confirmed behaviour: Meal Distribution's percentages are cut off rather
 * than rounded (68% + 31%), and the Logging streak counts logged items as
 * "meals".
 */
@Epic("Insights")
@Feature("Insights & calorie detail")
public class InsightsTest extends AbstractAppTest {

    private static final int BANANA = 105;
    private static final int APPLE = 95;
    private static final int TOTAL = 2 * BANANA + APPLE;

    private static final DateTimeFormatter HEATMAP_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter DETAIL_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DETAIL_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter TRENDS_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    /** A Calorie Detail range and what it should show. */
    private record RangeCase(String name, String title, LocalDate from, String caption) {
    }

    private InsightsPage insights() {
        return new InsightsPage(driver, factory).openTab();
    }

    /** The three foods, logged once for the class through Add Food. */
    private HomePage ensureFoodsLogged() {
        HomePage home = home();
        if (!home.hasLoggedFood("Banana")) {
            home = home.openFoodSearch().search("banana").open("Banana").selectMeal("Breakfast").log();
            home = home.openFoodSearch().search("banana").open("Banana").selectMeal("Breakfast").log();
            home = home.openFoodSearch().search("apple").open("Apple").selectMeal("Lunch").log();
        }
        return home.open();
    }

    /** Protein, carbs and fat taken today, from Calorie Detail's Day view. */
    private List<String> dayMacros(HomePage home) {
        home.openCalorieDetail();
        List<String> macros = new CalorieDetailPage(driver, factory).macrosTaken();
        home.closeCalorieDetail();
        return macros;
    }

    /** "SEP 30 – OCT 6" or "OCT 1 – 7": starts at {@code from}, ends at {@code to}. */
    private static boolean isRange(String label, String from, String toFull, String toShort) {
        return label.startsWith(from + " – ") && (label.endsWith(" – " + toFull) || label.endsWith(" – " + toShort));
    }

    // ---------------------------------------------------------------- empty

    @Test(groups = {"insights", "regression", "p1"}, priority = 0,
            description = "INS_001 Insights before anything is logged")
    @Description("Zero differences this week, Goals from the profile, zero streaks, empty trends and fasting, and no "
            + "Meal Distribution or Most Eaten yet")
    public void ins001_emptyState() {
        HomePage home = home();
        int goal = home.goalKcal();
        int proteinTarget = home.macros().get("Protein").targetG();
        InsightsPage page = insights();

        assertEquals(page.weeklyDifferences(), List.of(0, 0, 0), "this week vs last week");
        assertEquals(page.goals(), List.of("70.0", "65.0", String.format(Locale.US, "%,d", goal)), "Goals card");
        assertEquals(page.goalProgress(), "0% toward target");
        Map<String, Integer> score = page.healthScore();
        assertEquals(score.get("Score").intValue(),
                InsightsPage.SCORE_CATEGORIES.stream().mapToInt(score::get).sum(), "score is the sum: " + score);
        assertTrue(page.trendsShowsEmptyWeek(), "'No data for this week'");
        assertTrue(page.hasSection("Start a fast to see your progress here."), "fasting empty state");
        assertEquals(page.streaks(), List.of(
                new Streak("Logging", "0", "days", "Log a meal to start"),
                new Streak("Calorie Goal", "0", "days", "Hit your calorie window today"),
                new Streak("Protein Goal", "0", "days", "Hit " + proteinTarget + "g protein today"),
                new Streak("Water Goal", "0", "days", "Drink 8 glasses today")));
        assertFalse(page.hasSection("Meal Distribution"), "no Meal Distribution yet");
        assertFalse(page.hasSection("Most Eaten"), "no Most Eaten yet");
    }

    // ---------------------------------------------------------------- with food

    @Test(groups = {"insights", "smoke", "regression", "p0"}, priority = 1,
            description = "INS_002 This Week vs Last Week")
    @Description("After 305 kcal today and nothing last week: calories 305, protein as on Calorie Detail, 1 day")
    public void ins002_weeklyComparison() {
        HomePage home = ensureFoodsLogged();
        int protein = digits(dayMacros(home).get(0));

        assertEquals(insights().weeklyDifferences(), List.of(TOTAL, protein, 1));
    }

    @Test(groups = {"insights", "regression", "p1"}, priority = 1,
            description = "INS_003 Meal Distribution")
    @Description("Breakfast 210 kcal 68%, Lunch 95 kcal 31% — percentages cut off as the app shows them")
    public void ins003_mealDistribution() {
        ensureFoodsLogged();
        assertEquals(insights().mealDistribution(), List.of(
                new MealShare("Breakfast", 2 * BANANA + " kcal", "68%"),
                new MealShare("Lunch", APPLE + " kcal", "31%")));
    }

    @Test(groups = {"insights", "regression", "p1"}, priority = 1,
            description = "INS_004 Most Eaten")
    @Description("Banana is #1 with '2x logged · 105 kcal avg'; Apple, logged once, is not listed")
    public void ins004_mostEaten() {
        ensureFoodsLogged();
        List<String> mostEaten = insights().mostEaten();
        assertEquals(mostEaten.subList(0, 3), List.of("Banana", "#1", "2x logged · " + BANANA + " kcal avg"));
        assertFalse(mostEaten.contains("Apple"), "Apple logged once is not listed: " + mostEaten);
    }

    @Test(groups = {"insights", "regression", "p1"}, priority = 1,
            description = "INS_005 Nutrition Trends")
    @Description("This week shows 305 kcal with the same macro averages as Calorie Detail and 'NOW'; Month and Year "
            + "keep 305; going back a week is empty and Forward returns")
    public void ins005_nutritionTrends() {
        HomePage home = ensureFoodsLogged();
        List<String> macros = dayMacros(home);
        LocalDate today = home.deviceToday();
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7);

        InsightsPage page = insights();
        assertEquals(page.trendsHeadline(), TOTAL + " kcal");
        assertEquals(page.trendsAverageMacros(), macros, "averages equal Calorie Detail's Day macros");
        String week = page.trendsRange();
        assertTrue(week.startsWith(sunday.format(TRENDS_DAY) + " – ") && week.endsWith(", " + today.getYear()),
                "this week (Sunday–Saturday): " + week);
        assertTrue(page.trendsShowsNow(), "'NOW' on the current week");

        for (String period : List.of("Month", "Year")) {
            page.trendsTap(period);
            assertEquals(page.trendsHeadline(), TOTAL + " kcal", period);
        }
        page.trendsTap("Week");
        assertEquals(page.trendsRange(), week, "back to this week");

        page.trendsTap("Back");
        assertTrue(page.trendsShowsEmptyWeek(), "last week has no data");
        assertFalse(page.trendsShowsNow(), "no 'NOW' on a past week");
        page.trendsTap("Forward");
        assertEquals(page.trendsRange(), week);
        assertTrue(page.trendsShowsNow());
    }

    @Test(groups = {"insights", "regression", "p1"}, priority = 1,
            description = "INS_006 Calorie Detail Day, Week, Month, Year")
    @Description("Day: 305 of the daily goal with the macros taken; Week, Month and Year: their date ranges, a 305 "
            + "total and 305/day averaged over days with food")
    public void ins006_calorieDetail() {
        HomePage home = ensureFoodsLogged();
        int goal = home.goalKcal();
        LocalDate today = home.deviceToday();
        CalorieDetailPage detail = new CalorieDetailPage(driver, factory);

        home.openCalorieDetail();
        assertEquals(detail.title(), "Calories");
        assertEquals(detail.range(), "TODAY");
        assertEquals(detail.total(), TOTAL);
        assertTrue(detail.showsText("Total consumed today"));
        assertEquals(detail.goalLine(), "of " + String.format(Locale.US, "%,d", goal) + " goal");

        String end = today.format(DETAIL_DAY).toUpperCase(Locale.US);
        String endShort = String.valueOf(today.getDayOfMonth());
        for (RangeCase range : List.of(
                new RangeCase("Week", "Calories · Week", today.minusDays(6), "Total over the past week"),
                new RangeCase("Month", "Calories · Month", today.minusDays(29), "Total over the past month"))) {
            home.selectCalorieDetailRange(range.name());
            assertEquals(detail.title(), range.title());
            String from = range.from().format(DETAIL_DAY).toUpperCase(Locale.US);
            assertTrue(isRange(detail.range(), from, end, endShort), range.name() + " range: " + detail.range());
            assertEquals(detail.total(), TOTAL, range.name() + " total");
            assertEquals(detail.average(), TOTAL, range.name() + " average per logged day");
            assertTrue(detail.showsText(range.caption()), range.caption());
        }

        home.selectCalorieDetailRange("Year");
        assertEquals(detail.title(), "Calories · Year");
        assertEquals(detail.range(), (today.minusMonths(11).format(DETAIL_MONTH) + " – "
                + today.format(DETAIL_MONTH)).toUpperCase(Locale.US));
        assertEquals(detail.total(), TOTAL);
        assertTrue(detail.showsText("Total over the past 12 months"));
        home.closeCalorieDetail();
    }

    @Test(groups = {"insights", "regression", "p2"}, priority = 1,
            description = "INS_007 Streaks")
    @Description("Logging 1 day '3 meals logged today' (items, confirmed); calorie, protein and water at 0; Home says "
            + "1 of 4 streaks active")
    public void ins007_streaks() {
        HomePage home = ensureFoodsLogged();
        int proteinTarget = home.macros().get("Protein").targetG();
        assertTrue(home.streakBadgeLabel().startsWith("1 of 4 streaks active today"), home.streakBadgeLabel());

        assertEquals(insights().streaks(), List.of(
                new Streak("Logging", "1", "day", "3 meals logged today"),
                new Streak("Calorie Goal", "0", "days", "Hit your calorie window today"),
                new Streak("Protein Goal", "0", "days", "Hit " + proteinTarget + "g protein today"),
                new Streak("Water Goal", "0", "days", "Drink 8 glasses today")));
    }

    @Test(groups = {"insights", "regression", "p2"}, priority = 1,
            description = "INS_008 Health Score")
    @Description("Each category is between 0 and 20 and the Score is their sum; the explanation opens and closes")
    public void ins008_healthScore() {
        ensureFoodsLogged();
        InsightsPage page = insights();
        Map<String, Integer> score = page.healthScore();
        for (String category : InsightsPage.SCORE_CATEGORIES) {
            int value = score.get(category);
            assertTrue(value >= 0 && value <= 20, category + " in 0–20: " + value);
        }
        assertEquals(score.get("Score").intValue(),
                InsightsPage.SCORE_CATEGORIES.stream().mapToInt(score::get).sum(), "score is the sum: " + score);

        page.openScoreExplanation();
        assertTrue(page.hasSection("How your score is calculated"));
        page.closeScoreExplanation();
    }

    @Test(groups = {"insights", "regression", "p2"}, priority = 1,
            description = "INS_009 Calorie Heatmap")
    @Description("'Last 5 weeks' with its legend; today's cell reads today's calories; the day before has no data")
    public void ins009_heatmap() {
        HomePage home = ensureFoodsLogged();
        LocalDate today = home.deviceToday();
        InsightsPage page = insights();

        assertTrue(page.hasSection("Last 5 weeks"));
        for (String legend : List.of("No data", "Under", "On target", "Over")) {
            assertTrue(page.hasSection(legend), "legend " + legend);
        }
        String todayLabel = today.format(HEATMAP_DAY);
        assertEquals(page.heatmapCell(todayLabel), todayLabel + " — " + TOTAL + " kcal");
        String yesterday = today.minusDays(1).format(HEATMAP_DAY);
        assertEquals(page.heatmapCell(yesterday), yesterday + " — no data logged");
    }

    // ---------------------------------------------------------------- last week (simulator only)

    @Test(groups = {"insights", "regression", "p2"}, priority = 2,
            description = "INS_010 A food from last week")
    @Description("An Apple moved 9 days back counts as last week: the weekly differences become 305 − 95 = 210, "
            + "protein, and 0 days; Calorie Detail's Week leaves it out and Month includes it")
    public void ins010_lastWeekFood() {
        if (Config.isReal()) {
            throw new SkipException("Simulator-only: moves a food entry in the app's database");
        }
        HomePage home = ensureFoodsLogged();
        int proteinToday = digits(dayMacros(home).get(0));
        home = home.openFoodSearch().search("apple").open("Apple").selectMeal("Snack").log();

        assertTrue(AppStateResetter.backdateInstall(10), "install date moved 10 days back");
        assertTrue(AppStateResetter.shiftLatestFoodEntryBack("Apple", 9), "today's last Apple moved 9 days back");
        home = relaunchApp();

        List<Integer> differences = insights().weeklyDifferences();
        assertEquals(differences.get(0).intValue(), TOTAL - APPLE, "calories: this week minus last week");
        assertEquals(differences.get(2).intValue(), 0, "1 day this week, 1 day last week");
        assertTrue(differences.get(1) <= proteinToday, "protein difference: " + differences);

        home = home().open();
        home.openCalorieDetail();
        CalorieDetailPage detail = new CalorieDetailPage(driver, factory);
        home.selectCalorieDetailRange("Week");
        assertEquals(detail.total(), TOTAL, "the past week leaves the 9-day-old Apple out");
        home.selectCalorieDetailRange("Month");
        assertEquals(detail.total(), TOTAL + APPLE, "the past month includes it");
        home.closeCalorieDetail();
    }

    private static int digits(String text) {
        return Integer.parseInt(text.replaceAll("[^0-9]", ""));
    }
}
