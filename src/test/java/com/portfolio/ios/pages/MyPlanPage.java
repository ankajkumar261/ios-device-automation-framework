package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.time.Duration;
import java.util.List;

/**
 * "My Plan", the Meals tab once a plan is built ({@code MyPlanView.swift}),
 * and the "Building your …" cover that leads to it.
 *
 * <p>Verified on the simulator: "Reset Plan"; day Buttons "Today",
 * "Day, 2" …; the plan's name; slot tabs "🍳, Breakfast", "🥗, Lunch",
 * "🍎, Snack", "🍲, Dinner"; the day's eaten / target line "0/2,234 kcal";
 * one meal card at a time, a Button "Name, 559 kcal, 1 serving" with
 * "Swap"; "Logged" on it once logged; then the plan list below. Meal names
 * and calories are AI-written, so only their shape is checked.
 */
public final class MyPlanPage extends BasePage {

    public MyPlanPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /** Waits out the "Building your …" cover (production AI backend) and lands on My Plan. */
    @Step("Wait for the plan to be built")
    public MyPlanPage waitUntilBuilt(Duration timeout) {
        Waits.until(timeout, "the plan to be built (AI backend)", () -> {
            if (Waits.isPresent(driver, Locators.staticText("Couldn't build your plan"))) {
                throw new AssertionError("The backend could not build the plan ('Couldn't build your plan')");
            }
            return !Waits.isPresent(driver, Locators.planGenerationTitle())
                    && Waits.isPresent(driver, Locators.visibleButton("Reset Plan")) ? Boolean.TRUE : null;
        });
        return this;
    }

    public boolean isShowing() {
        return Waits.isPresent(driver, Locators.visibleButton("Reset Plan"));
    }

    @Step("Wait for My Plan")
    public MyPlanPage waitUntilLoaded() {
        visible(Locators.visibleButton("Reset Plan"), Waits.MEDIUM, "My Plan 'Reset Plan'");
        return this;
    }

    public boolean showsText(String label) {
        return Waits.isPresent(driver, Locators.staticText(label));
    }

    /** Day buttons, "Today", "Day, 2" … (those in the tree). */
    public List<String> dayButtons() {
        return elementsInOrder().stream()
                .filter(e -> "XCUIElementTypeButton".equals(e[0])
                        && (e[1].equals("Today") || e[1].matches("Day, [0-9]")))
                .map(e -> e[1]).toList();
    }

    public boolean hasSlotTab(String slot) {
        return Waits.isPresent(driver, Locators.myPlanSlotTab(slot));
    }

    @Step("Select day {day}")
    public MyPlanPage selectDay(String day) {
        tap(Locators.visibleButton(day), "My Plan day");
        visible(Locators.myPlanMealCard(), Waits.MEDIUM, "a meal card on " + day);
        return this;
    }

    @Step("Select slot {slot}")
    public MyPlanPage selectSlot(String slot) {
        tap(Locators.myPlanSlotTab(slot), "My Plan slot tab");
        visible(Locators.myPlanMealCard(), Waits.SHORT, "a meal card for " + slot);
        return this;
    }

    /** "Name, 559 kcal, 1 serving". */
    public String mealCardLabel() {
        return Waits.resilientGet(() -> visible(Locators.myPlanMealCard(), Waits.MEDIUM, "My Plan meal card")
                .getAttribute("label"));
    }

    public String mealName() {
        String label = mealCardLabel();
        return label.substring(0, label.lastIndexOf(", ", label.lastIndexOf(" kcal")));
    }

    public int mealKcal() {
        String label = mealCardLabel();
        String kcal = label.substring(label.lastIndexOf(", ", label.lastIndexOf(" kcal")) + 2,
                label.lastIndexOf(" kcal"));
        return digits(kcal);
    }

    /** The day's eaten / target, from "559/2,234 kcal". */
    public int[] dayTotal() {
        String[] parts = textOf(Locators.myPlanDayTotal(), Waits.SHORT).replace(" kcal", "").split("/");
        return new int[]{digits(parts[0]), digits(parts[1])};
    }

    public boolean showsLogged() {
        return isVisible(Locators.staticText("Logged"), Waits.SHORT);
    }

    @Step("Open the meal")
    public PlanMealDetailPage openMeal() {
        tap(Locators.myPlanMealCard(), "My Plan meal card");
        return new PlanMealDetailPage(driver, factory).waitUntilLoaded();
    }
}
