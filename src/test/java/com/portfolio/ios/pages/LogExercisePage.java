package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * "Log Exercise", opened by Home's "View all activities"
 * ({@code Features/Exercise/LogExerciseView.swift}).
 *
 * <p>Verified on the simulator, texts in order: "Today's Burn", total,
 * "kcal burn"; "Apple Health", "0 kcal", "Active energy"; "Logged exercise",
 * "345 kcal", "1 entry"; then "Choose activity", "TODAY'S ACTIVITY" and one
 * Button per entry, newest first: "Running, 30 min · 12:49 PM, 345 kcal".
 * "Or start a live workout timer" is pinned at the bottom.
 */
public final class LogExercisePage extends BasePage {

    public LogExercisePage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for Log Exercise")
    public LogExercisePage waitUntilLoaded() {
        visible(Locators.staticText("Log Exercise"), Waits.MEDIUM, "'Log Exercise' screen");
        return this;
    }

    /** The big number in the ring: Apple Health + logged exercise. */
    public int todaysBurn() {
        return digits(textAfter("Today's Burn", 1));
    }

    public int appleHealthKcal() {
        return digits(textAfter("Apple Health", 1));
    }

    public int loggedExerciseKcal() {
        return digits(textAfter("Logged exercise", 1));
    }

    /** "1 entry", "2 entries" or "Nothing logged yet". */
    public String entriesCaption() {
        return textAfter("Logged exercise", 2);
    }

    /** Labels of the entry rows, newest first. */
    public List<String> rows() {
        List<String> labels = new ArrayList<>();
        for (WebElement row : driver.findElements(Locators.exerciseRows())) {
            labels.add(Waits.resilientGet(() -> row.getAttribute("label")));
        }
        return labels;
    }

    /** The row label for {@code name}, or null. */
    public String row(String name) {
        List<WebElement> rows = driver.findElements(Locators.exerciseRow(name));
        return rows.isEmpty() ? null : Waits.resilientGet(() -> rows.get(0).getAttribute("label"));
    }

    @Step("Open the {name} entry")
    public ActivitySheet openRow(String name) {
        tap(Locators.exerciseRow(name), "Log Exercise row");
        visible(Locators.staticText("Edit activity"), Waits.SHORT, "'Edit activity' sheet");
        return new ActivitySheet(driver, factory).waitUntilLoaded();
    }

    @Step("Choose activity")
    public ActivityCatalogSheet chooseActivity() {
        tap(Locators.visibleButton("Choose activity"), "Log Exercise");
        return new ActivityCatalogSheet(driver, factory).waitUntilLoaded();
    }

    /** Logs {@code name} for {@code minutes} through "Choose activity"; back on this screen. */
    @Step("Log {name} for {minutes} min")
    public LogExercisePage log(String name, int minutes) {
        int rowsBefore = rows().size();
        ActivitySheet sheet = chooseActivity().search(name).pick(name);
        sheet.increase(Math.max(0, minutes - sheet.minutes()));
        sheet.decrease(Math.max(0, sheet.minutes() - minutes));
        sheet.add();
        waitUntilLoaded();
        Waits.untilTrue(Waits.SHORT, "a new row on Log Exercise", () -> rows().size() == rowsBefore + 1);
        return this;
    }

    @Step("Open the live workout timer")
    public LiveWorkoutPage startLiveWorkout() {
        tap(Locators.visibleButton("Or start a live workout timer"), "Log Exercise");
        return new LiveWorkoutPage(driver, factory).waitUntilLoaded();
    }

    /** Deletes every entry through the app's own Edit → Delete. */
    @Step("Delete every logged activity")
    public LogExercisePage deleteAll() {
        for (int i = 0; i < 20 && !rows().isEmpty(); i++) {
            int before = rows().size();
            String first = rows().get(0);
            String name = first.substring(0, first.indexOf(", "));
            openRow(name).tapDelete().confirmDelete();
            Waits.untilTrue(Waits.SHORT, "a row to go", () -> rows().size() == before - 1);
        }
        if (!rows().isEmpty()) {
            throw new AssertionError("Activities left after deleting: " + rows());
        }
        return this;
    }

    @Step("Close Log Exercise")
    public HomePage done() {
        tap(Locators.visibleButton("Done"), "Log Exercise");
        waitForGone(Locators.staticText("Log Exercise"), Waits.SHORT);
        return new HomePage(driver, factory);
    }

    private String textAfter(String caption, int offset) {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf(caption);
        if (at < 0 || at + offset >= texts.size()) {
            throw new AssertionError("No text " + offset + " after '" + caption + "' on Log Exercise; texts: " + texts);
        }
        return texts.get(at + offset);
    }
}
