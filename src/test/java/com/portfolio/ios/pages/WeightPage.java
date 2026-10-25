package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * The "Weight" sheet opened from Home's WEIGHT tile
 * ({@code Features/Home/WeightLogView.swift}).
 *
 * <p>Verified on the simulator: buttons "Done" (closes without saving), the
 * unit menu "kg"/"lbs" (items "Kilograms (kg)", "Pounds (lbs)"), "Remove" /
 * "Add" (±0.1) and "Log Weight"; texts "CURRENT" + value + unit and
 * "LOG WEIGHT" + value. The slider is a custom drag control with no
 * accessibility element, so values are set with the ±0.1 buttons.
 */
public final class WeightPage extends BasePage {

    public static final double LBS_PER_KG = 2.20462;

    public WeightPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the weight sheet")
    public WeightPage waitUntilLoaded() {
        visible(Locators.staticText("LOG WEIGHT"), Waits.MEDIUM, "weight sheet 'LOG WEIGHT'");
        return this;
    }

    /** The CURRENT card's value (latest logged weight, in the shown unit). */
    public double current() {
        return valueAfter("CURRENT");
    }

    /** The value about to be logged. */
    public double toLog() {
        return valueAfter("LOG WEIGHT");
    }

    /** "kg" or "lbs". */
    public String unit() {
        return Waits.resilientGet(() -> visible(Locators.weightUnitMenu(), Waits.SHORT, "unit menu")
                .getAttribute("label"));
    }

    @Step("Weight +0.1 × {times}")
    public WeightPage increase(int times) {
        return step("Add", times, +1);
    }

    @Step("Weight −0.1 × {times}")
    public WeightPage decrease(int times) {
        return step("Remove", times, -1);
    }

    /**
     * Logs the value. The button reads "Updated today's weight" when today
     * already had an entry (onboarding's, on the first day) or "Logged".
     *
     * @return the status text the button showed
     */
    @Step("Log Weight")
    public String log() {
        tap(Locators.visibleButton("Log Weight"), "weight sheet");
        WebElement status = visible(Locators.weightLoggedStatus(), Waits.SHORT, "weight logged status");
        return Waits.resilientGet(() -> status.getAttribute("label"));
    }

    /** Picks "Kilograms (kg)" or "Pounds (lbs)" from the unit menu. */
    @Step("Switch unit to {item}")
    public WeightPage selectUnit(String item) {
        String target = item.contains("lbs") ? "lbs" : "kg";
        tap(Locators.weightUnitMenu(), "unit menu");
        tap(Locators.visibleButton(item), "unit menu item");
        Waits.untilTrue(Waits.SHORT, "unit to become " + target, () -> target.equals(unit()));
        return this;
    }

    /** "Done" closes the sheet; it does not save. */
    @Step("Close the weight sheet")
    public HomePage done() {
        tap(Locators.visibleButton("Done"), "weight sheet");
        waitForGone(Locators.staticText("LOG WEIGHT"), Waits.SHORT);
        return new HomePage(driver, factory);
    }

    /** {@code kg} in pounds, rounded to 0.1 as the app displays it. */
    public static double toLbs(double kg) {
        return java.math.BigDecimal.valueOf(kg * LBS_PER_KG)
                .setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private WeightPage step(String button, int times, int sign) {
        double expected = round1(toLog() + sign * 0.1 * times);
        tapRepeatedly(Locators.visibleButton(button), times, "weight ±0.1");
        Waits.untilTrue(Waits.SHORT, "weight to become " + expected, () -> toLog() == expected);
        return this;
    }

    private double valueAfter(String caption) {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf(caption);
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No value after '" + caption + "' on the weight sheet; texts: " + texts);
        }
        return Double.parseDouble(texts.get(at + 1).trim());
    }

    /** Rounded to 0.1, as the sheet shows weights. */
    public static double round1(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
