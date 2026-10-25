package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.time.Duration;

/**
 * Base for every onboarding page: knows which step it represents, how to move
 * forward, and how to prove it actually arrived somewhere.
 *
 * <p>Steps 0-13 and 17-22 share one green "Continue" CTA, so
 * {@link #continueTo(OnboardingStep)} covers most of the flow. The video and
 * loader screens (14, 15, 23) auto-advance and have no CTA, and step 24 is a
 * press-and-hold — those pages override {@link #advanceTo(OnboardingStep)}.
 */
public abstract class StepPage extends BasePage {

    protected final OnboardingStep step;

    protected StepPage(IOSDriver driver, DriverFactory factory, OnboardingStep step) {
        super(driver, factory);
        this.step = step;
    }

    public OnboardingStep step() {
        return step;
    }

    /** Asserts this page's screen is on top. Call before acting, not after. */
    public boolean isCurrent() {
        return isVisible(step.markerLocator(), Waits.SHORT);
    }

    public void assertCurrent() {
        Waits.visible(driver, step.markerLocator(), Waits.MEDIUM,
                step.analyticsName() + " step heading");
    }

    /**
     * Taps Continue and waits until {@code next} is on screen.
     *
     * <p>Waiting for the destination rather than for the tap to "land" is what
     * keeps a slow spring transition from letting the next tap fire against the
     * previous step's still-mounted button — the single most likely cause of
     * silently skipping a step in this flow.
     */
    @Step("Continue from {from} to {to}")
    public void continueTo(OnboardingStep next) {
        advanceTo(next);
    }

    protected void advanceTo(OnboardingStep next) {
        advanceFrom(step, next);
    }

    /**
     * Advances from {@code from} to {@code to}.
     *
     * <p>The explicit {@code from} exists because several page objects cover a
     * run of consecutive steps — {@code DietLifestylePage} covers 8-13,
     * {@code BodyMetricsPage} covers 17-20 — and therefore cannot carry a single
     * "current step" field. Reusing this page's own {@link #step} for the check
     * works for the first transition in such a page and then fails forever after,
     * because by the second call the app is one or more steps past it. The
     * symptom is a timeout that names a step the app demonstrably left long ago,
     * which is why the failure artifact is worth capturing: it shows where the
     * app really is.
     *
     * <p>Verifying with {@link #waitForStep} rather than {@link #assertCurrent}
     * also means a mismatch reports which step is actually on screen.
     */
    protected void advanceFrom(OnboardingStep from, OnboardingStep to) {
        waitForStep(from, Waits.MEDIUM);
        tap(Locators.continueButton(),
                "Continue on " + from.analyticsName() + " (advancing to " + to.analyticsName() + ")");
        waitForStep(to, Waits.MEDIUM);
    }

    @Step("Tap Continue")
    protected void tapContinue() {
        tap(Locators.continueButton());
    }

    /**
     * Waits for {@code expected} to become current.
     *
     * <p>Some steps legitimately share headline copy with their neighbours, so
     * on timeout this reports every recognised step that is currently on screen
     * — which turns "step 9 never appeared" into "step 9 never appeared, the app
     * was still on EATING_WINDOW" instead of a bare timeout.
     */
    @Step("Wait for step {expected}")
    public void waitForStep(OnboardingStep expected, Duration timeout) {
        try {
            Waits.untilTrue(timeout,
                    "onboarding step " + expected.analyticsName(),
                    () -> expected.isCurrentNow(driver));
        } catch (Waits.WaitTimeout e) {
            throw new Waits.WaitTimeout(
                    describeWhereWeActuallyAre(expected), timeout, e.getCause());
        }
    }

    /**
     * Waits for {@code expected}, or for any later step.
     *
     * <p>For self-advancing steps only — see
     * {@link com.portfolio.ios.core.OnboardingStep#isAtOrBeyond}. Using this for a step
     * that waits for input would mask a genuinely stuck flow, so it is
     * deliberately not the default in {@link #waitForStep}.
     */
    @Step("Wait for step {expected} or later")
    public void waitForStepOrBeyond(OnboardingStep expected, Duration timeout) {
        try {
            Waits.untilTrue(timeout,
                    "onboarding step " + expected.analyticsName() + " or later",
                    () -> expected.isAtOrBeyond(driver));
        } catch (Waits.WaitTimeout e) {
            throw new Waits.WaitTimeout(
                    describeWhereWeActuallyAre(expected), timeout, e.getCause());
        }
    }

    /** Reports which step's marker is actually on screen, for failure messages. */
    private String describeWhereWeActuallyAre(OnboardingStep expected) {
        OnboardingStep[] steps = OnboardingStep.values();
        OnboardingStep actual = null;
        for (int i = expected.index(); i < steps.length && actual == null; i++) {
            if (steps[i].isCurrentNow(driver)) {
                actual = steps[i];
            }
        }
        for (int i = expected.index() - 1; i >= 0 && actual == null; i--) {
            if (steps[i].isCurrentNow(driver)) {
                actual = steps[i];
            }
        }
        return "expected onboarding step '" + expected.analyticsName() + "' but the app is on '"
                + (actual == null ? "<unrecognised screen>" : actual.analyticsName()) + "'";
    }
}