package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.core.WheelValues;
import com.portfolio.ios.model.OnboardingOptions;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Steps 8-13 — diet, eating window, health concerns, commitments, struggles,
 * and the closing benefit card.
 *
 * <p>Every screen here is a chip/card cluster plus a green Continue, so the
 * shared {@link #continueTo} covers navigation and each method only has to
 * express the choice it makes.
 */
public final class DietLifestylePage extends StepPage {

    public DietLifestylePage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.DIET);
    }

    /** Step 8. */
    @Step("Select diet: {diet}")
    public void selectDiet(OnboardingOptions.Diet diet) {
        Waits.visible(driver, Locators.headingContaining("Do You Have Any Dietary"), Waits.MEDIUM,
                "diet heading");
        tap(Locators.tappable(diet.label()));
        advanceFrom(OnboardingStep.DIET, OnboardingStep.EATING_WINDOW);
    }

    /**
     * Step 9 — sets both halves of the eating window.
     *
     * <p>Two {@code UIPickerView} wheels, Start first then Finish, rendered in
     * that order in the view tree. The app's hour list excludes the opposite
     * hour, so an equal pair is imphealthcareble; the default pair below avoids it.
     *
     * <p>Advance is deferred while a wheel is settling (the app guards against
     * tearing a spinning picker down), which is why the value is read back
     * before Continue is tapped.
     */
    @Step("Set eating window {start} to {end}")
    public void setEatingWindow(int startHour24, int endHour24) {
        Waits.until(Waits.MEDIUM, "two eating-window pickers", () -> {
            List<WebElement> wheels = driver.findElements(Locators.pickerWheel());
            return wheels.size() >= 2 ? wheels : null;
        });

        setWheelValue(wheelAt(0), formatHour(startHour24), WheelValues::hour24);
        setWheelValue(wheelAt(1), formatHour(endHour24), WheelValues::hour24);

        advanceFrom(OnboardingStep.EATING_WINDOW, OnboardingStep.HEALTH_CONCERNS);
    }

    /**
     * Step 10 — select any number of health concerns.
     *
     * <p>Empty is a legitimate selection here, so an empty list simply advances.
     */
    @Step("Select health concerns: {concerns}")
    public void selectHealthConcerns(List<OnboardingOptions.HealthConcern> concerns) {
        Waits.visible(driver, Locators.headingContaining("Do You Have Any"), Waits.MEDIUM,
                "health-concerns heading");
        for (OnboardingOptions.HealthConcern concern : concerns) {
            tap(Locators.tappable(concern.label()));
        }
        advanceFrom(OnboardingStep.HEALTH_CONCERNS, OnboardingStep.COMMITMENTS);
    }

    /** Step 11. */
    @Step("Select commitments: {commitments}")
    public void selectCommitments(List<OnboardingOptions.Commitment> commitments) {
        Waits.visible(driver, Locators.headingContaining("What Would You Like"), Waits.MEDIUM,
                "commitments heading");
        for (OnboardingOptions.Commitment commitment : commitments) {
            tap(Locators.tappable(commitment.label()));
        }
        advanceFrom(OnboardingStep.COMMITMENTS, OnboardingStep.STRUGGLES);
    }

    /** Step 12. */
    @Step("Select struggles: {struggles}")
    public void selectStruggles(List<OnboardingOptions.Struggle> struggles) {
        Waits.visible(driver, Locators.headingContaining("What do you struggle with"), Waits.MEDIUM,
                "struggles heading");
        for (OnboardingOptions.Struggle struggle : struggles) {
            tap(Locators.tappable(struggle.label()));
        }
        advanceFrom(OnboardingStep.STRUGGLES, OnboardingStep.BETTER_FOOD_CHOICES);
    }

    /**
     * Step 13 — the closing benefit card, which then hands off to the loader.
     *
     * <p>The CTA here is <b>not</b> Continue. The app's global bottom bar is
     * gated behind {@code currentStep > 26} and therefore never renders at all;
     * steps that need one draw their own inline button. Step 13's reads
     * "Meet My Coach", so tapping Continue here times out for the full duration
     * and leaves the app parked on this screen.
     *
     * <p>That button label is also why step 15's marker has to be type-scoped:
     * "Meet My Coach" is this step's button <i>and</i> step 15's caption.
     */
    @Step("Advance past the benefits card")
    public void advancePastBenefits() {
        waitForStep(OnboardingStep.BETTER_FOOD_CHOICES, Waits.MEDIUM);
        tap(Locators.byTypeAndLabel("XCUIElementTypeButton", "Meet My Coach"),
                "Step 13 CTA");
        // "or beyond", not exact: step 14 is the self-advancing loader and may
        // already have handed over to step 15 by the time this first polls.
        waitForStepOrBeyond(OnboardingStep.ALMOST_THERE, Waits.MEDIUM);
    }

    /**
     * Renders an hour the way the app does, matching {@code formatHour(_:)}:
     * 0 -> "12:00 am", 12 -> "12:00 pm", 20 -> "8:00 pm".
     */
    static String formatHour(int hour24) {
        int h = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return h + ":00 " + (hour24 < 12 ? "am" : "pm");
    }
}