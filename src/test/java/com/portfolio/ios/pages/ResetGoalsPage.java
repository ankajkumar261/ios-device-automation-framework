package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.time.Duration;

/**
 * Reset Goals, opened from Profile's pencil ({@code ResetGoalFlowView.swift}).
 *
 * <p>Verified on the simulator, every step pre-filled with the saved value:
 * "What is Your Gender?", "What Is Your Birth Year?" (wheel), "What is your
 * height?" and "What is your weight?" (the same ruler as onboarding), "What
 * is Your Goal?", "What is your desired weight?", the weekly pace (only for
 * lose / gain), "Tell us about your activity level.", "Do You Have Any
 * Dietary Preferences?", "Do You Have Any Health Concerns?" with "Pick a
 * plan", then "Pick your new plan" whose "Generate" rebuilds the meal plan on
 * the AI backend. Only after that succeeds are the new details saved, and
 * Profile shows "Goal updated successfully". "Close" leaves without saving.
 */
public final class ResetGoalsPage extends BasePage {

    public ResetGoalsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for Reset Goals")
    public ResetGoalsPage waitUntilLoaded() {
        visible(Locators.staticText("What is Your Gender?"), Waits.MEDIUM, "Reset Goals first step");
        return this;
    }

    public boolean offers(String option) {
        return Waits.isPresent(driver, Locators.visibleButton(option));
    }

    /** Taps Continue and waits for the next step's heading (matched by a fragment). */
    @Step("Continue to the step '{nextHeading}'")
    public ResetGoalsPage continueTo(String nextHeading) {
        tap(Locators.visibleButton("Continue"), "Reset Goals");
        visible(Locators.visibleTextContaining(nextHeading), Waits.MEDIUM, "Reset Goals step '" + nextHeading + "'");
        return this;
    }

    /** On "What is your weight?": drags the ruler to {@code kg}. */
    @Step("Set weight to {kg} kg")
    public ResetGoalsPage setWeightKg(double kg) {
        new RulerControl(driver).drive(kg, 0.1, "Reset Goals weight");
        return this;
    }

    /** From the last step, "Pick a plan" opens "Pick your new plan". */
    @Step("Pick a plan")
    public ResetGoalsPage pickAPlan() {
        tap(Locators.visibleButton("Pick a plan"), "Reset Goals");
        visible(Locators.staticText("Pick your new plan"), Waits.MEDIUM, "'Pick your new plan'");
        return this;
    }

    /**
     * Generates {@code plan} (production AI backend) and waits for Profile's
     * "Goal updated successfully", answered with OK.
     */
    @Step("Generate {plan} and wait for the update")
    public ProfilePage generate(String plan, Duration timeout) {
        new MealPlansPage(driver, factory).tapGenerate(plan);
        Waits.until(timeout, "'Goal updated successfully' (AI backend)", () -> {
            if (Waits.isPresent(driver, Locators.alertTitled("Couldn't build your new plan"))) {
                throw new AssertionError("Reset Goals failed: 'Couldn't build your new plan'");
            }
            return Waits.isPresent(driver, Locators.alertTitled("Goal updated successfully")) ? Boolean.TRUE : null;
        });
        tap(Locators.alertActionButton("OK"), "'Goal updated successfully'");
        waitForGone(Locators.alertTitled("Goal updated successfully"), Waits.SHORT);
        return new ProfilePage(driver, factory).waitUntilLoaded();
    }

    @Step("Close Reset Goals")
    public ProfilePage close() {
        tap(Locators.visibleButton("Close"), "Reset Goals");
        waitForGone(Locators.staticText("What is Your Gender?"), Waits.SHORT);
        return new ProfilePage(driver, factory).waitUntilLoaded();
    }
}
