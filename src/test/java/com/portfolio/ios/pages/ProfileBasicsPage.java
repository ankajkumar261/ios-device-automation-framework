package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.model.OnboardingOptions;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

/**
 * Steps 6-7 — sex, then year of birth.
 *
 * <p>Step 7 is the first genuinely awkward step: the year picker is a real
 * {@code UIPickerView}, and the app's own source documents a crash when one is
 * torn down while still spinning ({@code OnboardingView.advanceStep} defers the
 * step change until the wheel settles). Both the wheel and the advance below
 * respect that, so this suite does not reproduce the bug.
 */
public final class ProfileBasicsPage extends StepPage {

    /** What the wheel was actually left on, for reporting and assertions. */
    private String lastSelectedYear = "";

    public ProfileBasicsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.GENDER);
    }

    /**
     * The year the wheel actually settled on.
     *
     * <p>Exposed so tests can assert against the app's real state rather than
     * the value they asked for — a picker can select a neighbouring row and
     * still look like it succeeded.
     */
    public String lastSelectedYear() {
        return lastSelectedYear;
    }

    /** Step 6 — pick the sex card, then advance to year of birth. */
    @Step("Select sex: {sex}")
    public void selectSex(OnboardingOptions.Sex sex) {
        assertCurrent();
        tap(Locators.tappable(sex.label()));
        tapContinue();
        waitForStep(OnboardingStep.YEAR_OF_BIRTH, Waits.MEDIUM);
    }

    /**
     * Step 7 — scroll the {@code UIPickerView} to {@code year}, then advance.
     *
     * <p>Selection is delegated to {@code mobile: selectPickerWheelValue}, which
     * scrolls in small increments until the target is centred and fails loudly
     * if it never arrives. The wheel's reported value is then read back as
     * verification rather than assumed.
     */
    @Step("Set birth year to {year}")
    public void setBirthYear(int year) {
        Waits.until(Waits.MEDIUM, "birth-year picker wheel", () -> {
            WebElement wheel = driver.findElement(Locators.pickerWheel());
            return wheel.isDisplayed() ? wheel : null;
        });
        awaitWheelSettled();

        // Re-resolved inside the selection loop: SwiftUI rebuilds the picker's
        // hosting views on every change, so a captured reference goes stale.
        setWheelValue(() -> driver.findElement(Locators.pickerWheel()), String.valueOf(year));
        awaitWheelSettled();

        String actual = selectedWheelValue();
        if (!actual.contains(String.valueOf(year))) {
            throw new AssertionError("Birth-year picker shows '" + actual
                    + "' after selecting " + year);
        }
        lastSelectedYear = actual;
        tapContinue();
        waitForStep(OnboardingStep.DIET, Waits.MEDIUM);
    }

    /**
     * Blocks until the wheel stops changing.
     *
     * <p>Sleep-free: the wheel's value is compared across wait-loop evaluations
     * (400ms apart — comfortably past the app's ~0.2s settle guard) and the
     * method returns on the first pair that agrees. A spinning wheel keeps
     * disagreeing, so no fixed pause is ever paid and none is needed.
     */
    private void awaitWheelSettled() {
        String[] previous = new String[1];
        Waits.untilTrue(Waits.SHORT, "picker wheel to stop scrolling", () -> {
            String current = selectedWheelValue();
            boolean settled = current.equals(previous[0]);
            previous[0] = current;
            return settled;
        });
    }

    /**
     * The wheel's currently selected option text, or "" if unreadable.
     *
     * <p>Reads {@code value} first rather than falling back to a JavaScript
     * {@code arguments[0].value} read: XCUITest does not implement
     * {@code executeScript}, answering "Method is not implemented", so a JS
     * fallback here would turn a readable attribute into a hard failure.
     */
    private String selectedWheelValue() {
        try {
            WebElement wheel = driver.findElement(Locators.pickerWheel());
            String value = wheel.getAttribute("value");
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
            String text = wheel.getText();
            return text == null ? "" : text.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }
}