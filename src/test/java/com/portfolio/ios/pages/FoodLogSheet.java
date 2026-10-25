package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * The sheet that opens from a food row ({@code ManualSearchView.swift:1011}):
 * meal chips, a quantity stepper, live calories and macros, Log / Cancel.
 *
 * <p>Verified on the simulator: meal chips are Buttons "Breakfast" … "Snack"
 * with no readable selected state (colour only), so tests always pick one
 * explicitly — the default depends on the time of day. The stepper is
 * "minus"/"plus" (labels "Remove"/"Add"), the quantity a TextField. Counted
 * items say "Servings" (step 1); g/ml items say "Amount" (step 5).
 *
 * <p>The sheet <i>rounds</i> calories and macros while Home truncates them
 * (open question with the iOS team), so assertions should stick to values
 * where both agree.
 */
public final class FoodLogSheet extends BasePage {

    public static final List<String> MEALS = List.of("Breakfast", "Lunch", "Dinner", "Snack");

    public FoodLogSheet(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /**
     * Waits for the sheet in either mode: "Log" when opened from search,
     * "Save" when opened from a logged food's Edit.
     */
    @Step("Wait for the log sheet")
    public FoodLogSheet waitUntilLoaded() {
        Waits.untilTrue(Waits.MEDIUM, "log sheet ('Log' or 'Save' button)", this::isShowing);
        return this;
    }

    public boolean isShowing() {
        return Waits.isPresent(driver, Locators.button("Log"))
                || Waits.isPresent(driver, Locators.button("Save"));
    }

    /** "Servings" for counted items, "Amount" for g/ml items. */
    public String quantityCaption() {
        if (Waits.isPresent(driver, Locators.staticText("Amount"))) {
            return "Amount";
        }
        return Waits.isPresent(driver, Locators.staticText("Servings")) ? "Servings" : null;
    }

    @Step("Choose meal {meal}")
    public FoodLogSheet selectMeal(String meal) {
        tap(Locators.button(meal), "meal chip");
        return this;
    }

    public String quantity() {
        return Waits.resilientGet(() -> visible(Locators.logSheetQuantityField(), Waits.SHORT, "quantity field")
                .getAttribute("value"));
    }

    @Step("Increase the quantity one step")
    public FoodLogSheet increase() {
        String before = quantity();
        tap(Locators.logSheetStepper("plus"), "quantity +");
        waitForQuantityChangeFrom(before);
        return this;
    }

    /** One step down; at the minimum the value stays put, so nothing is awaited. */
    @Step("Decrease the quantity one step")
    public FoodLogSheet decrease() {
        tap(Locators.logSheetStepper("minus"), "quantity −");
        return this;
    }

    @Step("Type quantity {text}")
    public FoodLogSheet setQuantity(String text) {
        WebElement field = visible(Locators.logSheetQuantityField(), Waits.SHORT, "quantity field");
        Waits.resilient(field::click);
        Waits.resilient(field::clear);
        Waits.resilient(() -> field.sendKeys(text));
        if (Waits.isPresent(driver, Locators.button("Done"))) {
            tap(Locators.button("Done"), "keyboard Done");
        }
        return this;
    }

    /** Live calories: the number shown just before the "kcal" caption. */
    public int kcal() {
        List<String[]> elements = elementsInOrder();
        for (int i = 1; i < elements.size(); i++) {
            String[] element = elements.get(i);
            if ("XCUIElementTypeStaticText".equals(element[0]) && "kcal".equals(element[1])) {
                for (int j = i - 1; j >= 0; j--) {
                    if ("XCUIElementTypeStaticText".equals(elements.get(j)[0])) {
                        return digits(elements.get(j)[1]);
                    }
                }
            }
        }
        throw new AssertionError("No calorie value before 'kcal' on the log sheet");
    }

    /** Grams on a macro chip: prefix "P:", "C:" or "F:" → e.g. 27 for "C:27g". */
    public int macroGrams(String prefix) {
        String label = Waits.resilientGet(() -> visible(Locators.logSheetMacro(prefix), Waits.SHORT,
                "macro chip " + prefix).getAttribute("label"));
        return digits(label);
    }

    public boolean isLogEnabled() {
        return Waits.resilientGet(() -> visible(Locators.button("Log"), Waits.SHORT, "Log button").isEnabled());
    }

    public boolean isSaveEnabled() {
        return Waits.resilientGet(() -> visible(Locators.button("Save"), Waits.SHORT, "Save button").isEnabled());
    }

    /** Edit mode: saves the change; the food detail stays open and updates in place. */
    @Step("Save the edit")
    public FoodDetailPage save() {
        tap(Locators.button("Save"), "edit sheet");
        waitForGone(Locators.button("Save"), Waits.MEDIUM);
        return new FoodDetailPage(driver, factory).waitUntilLoaded();
    }

    /** Edit mode: closes the sheet without saving, back on the food detail. */
    @Step("Cancel the edit")
    public FoodDetailPage cancelEdit() {
        dismissSheet();
        return new FoodDetailPage(driver, factory).waitUntilLoaded();
    }

    /** Logs the entry; the app dismisses the sheet and switches to Home. */
    @Step("Log the food")
    public HomePage log() {
        tap(Locators.button("Log"), "log sheet");
        waitForGone(Locators.button("Log"), Waits.MEDIUM);
        return new HomePage(driver, factory).settleAfterLog();
    }

    /**
     * Closes the sheet without logging. Add Food behind it has its own
     * Cancel, so the lowest Cancel on screen — the sheet's — is the one tapped.
     */
    @Step("Cancel the log sheet")
    public FoodSearchPage cancel() {
        dismissSheet();
        return new FoodSearchPage(driver, factory);
    }

    /** Taps the sheet's own Cancel — the lowest one on screen — and waits for the sheet to go. */
    private void dismissSheet() {
        WebElement lowest = Waits.until(Waits.SHORT, "log sheet Cancel", () -> {
            WebElement best = null;
            for (WebElement cancel : driver.findElements(Locators.button("Cancel"))) {
                if (cancel.isDisplayed()
                        && (best == null || cancel.getRect().getY() > best.getRect().getY())) {
                    best = cancel;
                }
            }
            return best;
        });
        Waits.resilient(lowest::click);
        Waits.untilTrue(Waits.SHORT, "log sheet to close", () -> !isShowing());
    }

    private void waitForQuantityChangeFrom(String before) {
        Waits.untilTrue(Waits.SHORT, "quantity to change from " + before,
                () -> !before.equals(quantity()));
    }
}
