package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

/**
 * A plan meal's detail, opened from My Plan ({@code MealDetailView.swift}).
 *
 * <p>Verified on the simulator: the slot ("BREAKFAST"), name, "SERVINGS",
 * "1 × serving", "−" (off at 1) / "+", macros, then "Ingredients",
 * "Directions", "Allergens"; "Back"; and the log Button "Log this meal,
 * 559 kcal" — "Future day — can't log yet, 550 kcal" (disabled) on a day
 * ahead. Logging adds a food entry for today and returns to My Plan.
 */
public final class PlanMealDetailPage extends BasePage {

    public PlanMealDetailPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the meal detail")
    public PlanMealDetailPage waitUntilLoaded() {
        visible(Locators.staticText("SERVINGS"), Waits.MEDIUM, "meal detail 'SERVINGS'");
        return this;
    }

    /** "BREAKFAST", "LUNCH", "SNACK" or "DINNER" — the first text. */
    public String slot() {
        return visibleTextsInOrder().get(0);
    }

    public String name() {
        return visibleTextsInOrder().get(1);
    }

    /** From "2 × serving(s)". */
    public int servings() {
        for (String text : visibleTextsInOrder()) {
            if (text.matches("[0-9]+ × servings?")) {
                return digits(text.substring(0, text.indexOf(' ')));
            }
        }
        throw new AssertionError("No servings line on the meal detail: " + visibleTextsInOrder());
    }

    /** Sections present in the tree (they may sit below the fold). */
    public boolean hasSection(String title) {
        return elementsInOrder().stream()
                .anyMatch(e -> "XCUIElementTypeStaticText".equals(e[0]) && title.equals(e[1]));
    }

    /** "Log this meal, 559 kcal", "Future day — can't log yet, 550 kcal", … */
    public String logButtonLabel() {
        return Waits.resilientGet(() -> visible(Locators.planMealLogButton(), Waits.SHORT, "meal log button")
                .getAttribute("label"));
    }

    /** The calories on the log button. */
    public int logKcal() {
        String label = logButtonLabel();
        return digits(label.substring(label.lastIndexOf(", ") + 2));
    }

    public boolean isLogEnabled() {
        return Waits.resilientGet(() -> driver.findElement(Locators.planMealLogButton()).isEnabled());
    }

    public boolean isStepEnabled(String name) {
        return Waits.resilientGet(() -> visible(Locators.visibleButtonNamed(name), Waits.SHORT, "servings " + name)
                .isEnabled());
    }

    @Step("Servings {name}")
    public PlanMealDetailPage step(String name) {
        int before = servings();
        tap(Locators.visibleButtonNamed(name), "servings " + name);
        int expected = before + ("plus".equals(name) ? 1 : -1);
        Waits.untilTrue(Waits.SHORT, "servings to become " + expected, () -> servings() == expected);
        return this;
    }

    /** Logs the meal; the detail closes back onto My Plan. */
    @Step("Log this meal")
    public MyPlanPage log() {
        tap(Locators.planMealLogButton(), "meal detail");
        waitForGone(Locators.staticText("SERVINGS"), Waits.MEDIUM);
        return new MyPlanPage(driver, factory).waitUntilLoaded();
    }

    @Step("Back to My Plan")
    public MyPlanPage back() {
        tap(Locators.visibleButton("Back"), "meal detail");
        waitForGone(Locators.staticText("SERVINGS"), Waits.SHORT);
        return new MyPlanPage(driver, factory).waitUntilLoaded();
    }
}
