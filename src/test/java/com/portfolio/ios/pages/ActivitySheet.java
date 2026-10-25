package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * The three activity sheets that share one layout — a duration stepper and a
 * "Calories burned" figure ({@code LogActivitiesSheet.swift},
 * {@code LogExerciseView.swift}):
 * <ul>
 *   <li>a catalog activity's duration sheet — "Add";</li>
 *   <li>"Describe" — workout name, intensity, "Add" (disabled without a name);</li>
 *   <li>"Edit activity" — "Save" (disabled until the duration changes) and
 *       "Delete" with a "Delete activity?" alert.</li>
 * </ul>
 * All three close with "Cancel" without saving. Duration moves 1 min per tap
 * between 5 and 180.
 */
public final class ActivitySheet extends BasePage {

    public ActivitySheet(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the activity sheet")
    public ActivitySheet waitUntilLoaded() {
        visible(Locators.staticText("Calories burned"), Waits.MEDIUM, "activity sheet 'Calories burned'");
        return this;
    }

    /** The duration in minutes, from "30 min". */
    public int minutes() {
        return digits(textOf(Locators.activityDuration(), Waits.SHORT));
    }

    /** The calories shown under the duration, from "345 kcal". */
    public int kcal() {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf("Calories burned");
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No value after 'Calories burned'; texts: " + texts);
        }
        return digits(texts.get(at + 1));
    }

    @Step("Duration +1 min × {times}")
    public ActivitySheet increase(int times) {
        return step("plus", times, +1);
    }

    @Step("Duration −1 min × {times}")
    public ActivitySheet decrease(int times) {
        return step("minus", times, -1);
    }

    /**
     * Taps "−" until the duration stops falling, and returns the floor it
     * stopped at. A tap that changes nothing is detected by a short poll.
     */
    @Step("Decrease the duration to its minimum")
    public int decreaseToMinimum() {
        // The slider reaches the lowest value in one move; then a tap on −
        // must leave it there — that is the clamp under test.
        WebElement slider = visible(Locators.visibleOfType("XCUIElementTypeSlider"), Waits.SHORT, "duration slider");
        Waits.resilient(() -> slider.sendKeys("0"));
        int[] last = {-1};
        int floor = Waits.until(Waits.SHORT, "the duration to settle at its minimum", () -> {
            int now = minutes();
            boolean settled = now == last[0];
            last[0] = now;
            return settled ? now : null;
        });
        tap(Locators.visibleButtonNamed("minus"), "duration −");
        if (Waits.untilQuietly(Waits.QUICK, () -> minutes() != floor)) {
            throw new AssertionError("− went below the minimum " + floor + " min to " + minutes() + " min");
        }
        return floor;
    }

    // ---------------------------------------------------------------- new entry (catalog / Describe)

    public boolean isAddEnabled() {
        return Waits.resilientGet(() -> visible(Locators.visibleButtonNamed("Add"), Waits.SHORT, "sheet Add")
                .isEnabled());
    }

    /** Logs the entry; the sheet (and the catalog behind it) close. */
    @Step("Add the activity")
    public void add() {
        tap(Locators.visibleButtonNamed("Add"), "activity sheet Add");
        waitForGone(Locators.staticText("Calories burned"), Waits.SHORT);
        waitForGone(Locators.staticText("Log activities"), Waits.SHORT);
    }

    /** Replaces the workout name and ends editing (Return), so the keyboard is gone. */
    @Step("Name the workout \"{name}\"")
    public ActivitySheet enterName(String name) {
        WebElement field = visible(Locators.visibleTextField(), Waits.SHORT, "workout name field");
        Waits.resilient(field::clear);
        typeInto(field, name + "\n");
        return this;
    }

    /** "Light", "Moderate" or "Intense"; waits for the calories to follow. */
    @Step("Choose intensity {level}")
    public ActivitySheet chooseIntensity(String level, int expectedKcal) {
        tap(Locators.visibleButton(level), "intensity");
        Waits.untilTrue(Waits.SHORT, "calories to become " + expectedKcal, () -> kcal() == expectedKcal);
        return this;
    }

    // ---------------------------------------------------------------- edit entry

    public boolean isSaveEnabled() {
        return Waits.resilientGet(() -> visible(Locators.visibleButton("Save"), Waits.SHORT, "sheet Save")
                .isEnabled());
    }

    @Step("Save the activity")
    public LogExercisePage save() {
        tap(Locators.visibleButton("Save"), "edit activity Save");
        waitForGone(Locators.staticText("Edit activity"), Waits.SHORT);
        return new LogExercisePage(driver, factory).waitUntilLoaded();
    }

    @Step("Tap Delete")
    public ActivitySheet tapDelete() {
        tap(Locators.visibleButton("Delete"), "edit activity Delete");
        visible(Locators.alertTitled("Delete activity?"), Waits.SHORT, "'Delete activity?' alert");
        return this;
    }

    public boolean deleteAlertSays(String message) {
        return Waits.isPresent(driver, Locators.staticText(message));
    }

    @Step("Confirm Delete")
    public LogExercisePage confirmDelete() {
        tap(Locators.alertActionButton("Delete"), "'Delete activity?' alert");
        waitForGone(Locators.alertTitled("Delete activity?"), Waits.SHORT);
        waitForGone(Locators.staticText("Edit activity"), Waits.SHORT);
        return new LogExercisePage(driver, factory).waitUntilLoaded();
    }

    @Step("Cancel the delete")
    public ActivitySheet cancelDelete() {
        tap(Locators.alertActionButton("Cancel"), "'Delete activity?' alert");
        waitForGone(Locators.alertTitled("Delete activity?"), Waits.SHORT);
        return waitUntilLoaded();
    }

    // ---------------------------------------------------------------- any of the three

    /** Closes the sheet without saving. */
    @Step("Cancel the activity sheet")
    public void cancel() {
        tap(Locators.visibleButton("Cancel"), "activity sheet Cancel");
        waitForGone(Locators.staticText("Calories burned"), Waits.SHORT);
    }

    private ActivitySheet step(String button, int times, int sign) {
        int expected = minutes() + sign * times;
        tapRepeatedly(Locators.visibleButtonNamed(button), times, "duration ±1");
        Waits.untilTrue(Waits.SHORT, "duration to become " + expected + " min", () -> minutes() == expected);
        return this;
    }
}
