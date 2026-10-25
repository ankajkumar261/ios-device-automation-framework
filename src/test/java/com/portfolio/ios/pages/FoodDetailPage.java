package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.util.List;

/**
 * Full-screen detail of one logged food, opened by tapping its row in Home's
 * timeline ({@code LoggedMealDetailSheet}, {@code MealTimelineView.swift:582}).
 *
 * <p>Verified on the simulator: buttons "Back", "Edit", "Trash"; a header of
 * texts name · meal · time · portion ("Greek Yogurt", "Dinner", "·",
 * "7:09 PM", "·", "170 g"); the calories as a bare number ("100") and the
 * macros as caption + value ("Carbs", "6.0g"). Home stays in the tree
 * underneath, so every read here uses visible texts only.
 */
public final class FoodDetailPage extends BasePage {

    public FoodDetailPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the food detail")
    public FoodDetailPage waitUntilLoaded() {
        visible(Locators.button("Trash"), Waits.MEDIUM, "food detail 'Trash' button");
        return this;
    }

    public boolean isShowing() {
        return Waits.isPresent(driver, Locators.button("Trash"));
    }

    /** The header line: name, meal, "·", time, "·", portion. */
    private List<String> header() {
        List<String> texts = visibleTextsInOrder();
        int firstDot = texts.indexOf("·");
        if (firstDot < 2 || firstDot + 3 >= texts.size() || !"·".equals(texts.get(firstDot + 2))) {
            throw new AssertionError("Food detail header not found; visible texts: " + texts);
        }
        return List.of(texts.get(firstDot - 2), texts.get(firstDot - 1), texts.get(firstDot + 3));
    }

    public String name() {
        return header().get(0);
    }

    /** "Breakfast", "Lunch", "Dinner" or "Snack". */
    public String meal() {
        return header().get(1);
    }

    /** e.g. "1 medium", "170 g". */
    public String portion() {
        return header().get(2);
    }

    /** Calories in the ring: the only visible text that is a bare number. */
    public int kcal() {
        for (String text : visibleTextsInOrder()) {
            if (text.matches("\\d[\\d,]*")) {
                return digits(text);
            }
        }
        throw new AssertionError("No calorie number on the food detail; texts: " + visibleTextsInOrder());
    }

    /** Grams shown under "Protein" / "Carbs" / "Fat", e.g. 6.0 for "6.0g". */
    public double macroGrams(String caption) {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf(caption);
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No value after '" + caption + "' on the food detail; texts: " + texts);
        }
        return Double.parseDouble(texts.get(at + 1).replace("g", "").trim());
    }

    @Step("Edit the logged food")
    public FoodLogSheet edit() {
        tap(Locators.button("Edit"), "food detail Edit");
        return new FoodLogSheet(driver, factory).waitUntilLoaded();
    }

    /** Opens the delete confirmation. */
    @Step("Tap the trash button")
    public FoodDetailPage tapTrash() {
        tap(Locators.button("Trash"), "food detail Trash");
        visible(Locators.deleteEntryAlert(), Waits.SHORT, "'Delete this entry?' alert");
        return this;
    }

    public boolean isDeleteAlertShowing() {
        return Waits.isPresent(driver, Locators.deleteEntryAlert());
    }

    /** Confirms the delete; the app closes the detail and returns to Home. */
    @Step("Confirm delete")
    public HomePage confirmDelete() {
        tap(Locators.alertActionButton("Delete"), "delete alert");
        waitForGone(Locators.button("Trash"), Waits.MEDIUM);
        return new HomePage(driver, factory).settleAfterLog();
    }

    /** Dismisses the delete alert; the detail stays open. */
    @Step("Cancel delete")
    public FoodDetailPage cancelDelete() {
        tap(Locators.alertActionButton("Cancel"), "delete alert");
        waitForGone(Locators.deleteEntryAlert(), Waits.SHORT);
        return this;
    }

    @Step("Back to Home")
    public HomePage back() {
        tap(Locators.button("Back"), "food detail Back");
        waitForGone(Locators.button("Trash"), Waits.MEDIUM);
        return new HomePage(driver, factory).settleAfterLog();
    }
}
