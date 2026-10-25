package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Insights tab ({@code Features/Insights/InsightsView.swift}). Every
 * figure is calculated on the device from logged food.
 *
 * <p>Verified on the simulator, texts in tree order: "This Week vs Last Week"
 * and three differences (calories, protein, days logged); "Goals" with
 * Start / Target / Daily; "Health Score" with Calories, Macros, Consistency,
 * Progress, Hydration and the total; "Calorie Heatmap" whose day cells read
 * "Tuesday, October 6 — 305 kcal"; "Nutrition Trends" with Week / Month /
 * Year, "Back" / "Forward", the range ("Oct 4 – 10, 2026") and "NOW";
 * "Meal Distribution"; steps; "Fasting"; "Streaks"; "Most Eaten". Sections
 * below the fold are read from the whole tree; cells and buttons are
 * scrolled to first.
 */
public final class InsightsPage extends BasePage {

    public static final List<String> SCORE_CATEGORIES =
            List.of("Calories", "Macros", "Consistency", "Progress", "Hydration");

    /** One "Meal Distribution" row. */
    public record MealShare(String meal, String kcal, String percent) {
    }

    /** One streak card: name, count, unit ("day" / "days"), and its line. */
    public record Streak(String name, String count, String unit, String line) {
    }

    public InsightsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Open the Insights tab")
    public InsightsPage openTab() {
        new HomePage(driver, factory).openTab("Insights");
        Waits.until(Waits.MEDIUM, "Insights 'Goals'",
                () -> driver.findElements(Locators.staticText("Goals")).isEmpty() ? null : Boolean.TRUE);
        return this;
    }

    public boolean hasSection(String title) {
        return allTexts().contains(title);
    }

    /** The {@code offset}-th text after the first {@code caption}. */
    public String textAfter(String caption, int offset) {
        List<String> texts = allTexts();
        int at = texts.indexOf(caption);
        if (at < 0 || at + offset >= texts.size()) {
            throw new AssertionError("No text " + offset + " after '" + caption + "' on Insights; texts: " + texts);
        }
        return texts.get(at + offset);
    }

    /** This week minus last week: calories, protein and days logged (as shown, without sign). */
    public List<Integer> weeklyDifferences() {
        List<Integer> values = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            values.add(digits(textAfter("This Week vs Last Week", i)));
        }
        return values;
    }

    /** Goals card: Start, Target and Daily values ("70.0", "65.0", "2,234"). */
    public List<String> goals() {
        return List.of(textAfter("Goals", 4), textAfter("Goals", 5), textAfter("Goals", 6));
    }

    /** The Goals card's progress line, "0% toward target". */
    public String goalProgress() {
        for (String text : allTexts()) {
            if (text.endsWith("toward target")) {
                return text;
            }
        }
        throw new AssertionError("No progress line on the Goals card");
    }

    /** Health Score categories and the total (key "Score"). */
    public Map<String, Integer> healthScore() {
        List<String> section = between("Health Score", "Calorie Heatmap");
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<Integer> unpaired = new ArrayList<>();
        for (int i = 0; i < section.size(); i++) {
            String text = section.get(i);
            if (SCORE_CATEGORIES.contains(text) && i + 1 < section.size() && section.get(i + 1).matches("[0-9]+")) {
                scores.put(text, Integer.parseInt(section.get(i + 1)));
                i++;
            } else if (text.matches("[0-9]+")) {
                unpaired.add(Integer.parseInt(text));
            }
        }
        if (unpaired.size() != 1) {
            throw new AssertionError("Expected one total on the Health Score card, found " + unpaired + " in " + section);
        }
        scores.put("Score", unpaired.get(0));
        return scores;
    }

    /** Taps the Health Score ⓘ; the card expands with "How your score is calculated". */
    @Step("Open the Health Score explanation")
    public InsightsPage openScoreExplanation() {
        tap(Locators.buttonIcon("Health Score"), "Health Score ⓘ");
        visible(Locators.staticText("How your score is calculated"), Waits.SHORT, "score explanation");
        return this;
    }

    /** Taps the ⓘ again; the explanation folds away. */
    @Step("Close the Health Score explanation")
    public InsightsPage closeScoreExplanation() {
        tap(Locators.buttonIcon("Health Score"), "Health Score ⓘ");
        waitForGone(Locators.staticText("How your score is calculated"), Waits.SHORT);
        return this;
    }

    /** Scrolls to a heatmap cell (they load only near the screen) and returns its label. */
    public String heatmapCell(String datePrefix) {
        WebElement cell = scrollIntoView(Locators.byLabelContaining(datePrefix + " — "), "heatmap cell " + datePrefix);
        return Waits.resilientGet(() -> cell.getAttribute("label"));
    }

    /** "Nutrition Trends": its headline value, "305 kcal". */
    public String trendsHeadline() {
        return textAfter("Nutrition Trends", 1);
    }

    /** The range shown under the period buttons, e.g. "Oct 4 – 10, 2026". */
    public String trendsRange() {
        List<String> texts = between("Nutrition Trends", "Meal Distribution", "Fasting");
        for (String text : texts) {
            if (text.matches(".*[0-9]{4}") || text.matches("[A-Z][a-z]+ [0-9]{4}")) {
                return text;
            }
        }
        throw new AssertionError("No range on Nutrition Trends: " + texts);
    }

    public boolean trendsShowsNow() {
        return between("Nutrition Trends", "Meal Distribution", "Fasting").contains("NOW");
    }

    public boolean trendsShowsEmptyWeek() {
        return between("Nutrition Trends", "Meal Distribution", "Fasting").contains("No data for this week");
    }

    /** The three texts before "Avg": average protein, carbs and fat ("3g", "79g", "1g"). */
    public List<String> trendsAverageMacros() {
        List<String> texts = between("Nutrition Trends", "Meal Distribution", "Fasting");
        int at = texts.indexOf("Avg");
        if (at < 3) {
            throw new AssertionError("No averages on Nutrition Trends: " + texts);
        }
        return texts.subList(at - 3, at);
    }

    /** Week, Month, Year, Back or Forward on Nutrition Trends. */
    @Step("Nutrition Trends: {button}")
    public InsightsPage trendsTap(String button) {
        WebElement control = scrollIntoView(Locators.visibleButton(button), "Nutrition Trends " + button);
        String before = trendsRange();
        Waits.resilient(control::click);
        if (List.of("Back", "Forward").contains(button)) {
            Waits.untilTrue(Waits.SHORT, "the range to change from " + before, () -> !trendsRange().equals(before));
        } else {
            Waits.untilQuietly(Waits.SHORT, () -> !trendsRange().equals(before));
        }
        return this;
    }

    public List<MealShare> mealDistribution() {
        List<String> texts = between("Meal Distribution", "steps", "Fasting");
        List<MealShare> rows = new ArrayList<>();
        for (int i = 0; i + 2 < texts.size(); i++) {
            if (List.of("Breakfast", "Lunch", "Dinner", "Snack").contains(texts.get(i))) {
                rows.add(new MealShare(texts.get(i), texts.get(i + 1), texts.get(i + 2)));
                i += 2;
            }
        }
        return rows;
    }

    /** The four streak cards; they load only when scrolled near, so they are scrolled to first. */
    public List<Streak> streaks() {
        scrollIntoView(Locators.staticText("Water Goal"), "streak cards");
        List<String> texts = between("Streaks", "Most Eaten", null);
        List<Streak> cards = new ArrayList<>();
        for (int i = 0; i + 3 < texts.size(); i++) {
            if (List.of("Logging", "Calorie Goal", "Protein Goal", "Water Goal").contains(texts.get(i))) {
                cards.add(new Streak(texts.get(i), texts.get(i + 1), texts.get(i + 2), texts.get(i + 3)));
                i += 3;
            }
        }
        return cards;
    }

    /** "Most Eaten" lines: name, rank, "2x logged · 105 kcal avg" — flattened in order. */
    public List<String> mostEaten() {
        return between("Most Eaten", null, null);
    }

    /** Texts after {@code from} up to the first of {@code until} / {@code orUntil} (null = to the end). */
    private List<String> between(String from, String until, String orUntil) {
        List<String> texts = allTexts();
        int start = texts.indexOf(from);
        List<String> out = new ArrayList<>();
        for (int i = start + 1; start >= 0 && i < texts.size(); i++) {
            String text = texts.get(i);
            if (text.equals(until) || text.equals(orUntil)) {
                break;
            }
            out.add(text);
        }
        return out;
    }

    private List<String> between(String from, String until) {
        return between(from, until, null);
    }

    /** Every StaticText in tree order, visible or not (Insights is one long scroll). */
    private List<String> allTexts() {
        List<String> texts = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        return texts;
    }
}
