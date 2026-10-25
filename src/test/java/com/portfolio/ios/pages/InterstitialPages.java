package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

/**
 * The non-data screens: the "Almost there..." loader (14), the Meet-the-coach
 * video (15), the Let's-get-started card (16), and the thanks video (23).
 *
 * <p>These are the steps where a naive {@code tap(Continue)} silently does
 * nothing, because there is no Continue — two of them advance on their own and
 * two have their own bespoke CTA. Getting this wrong shows up as a flow that
 * silently stalls for the full timeout, so each is handled explicitly.
 */
public final class InterstitialPages extends StepPage {

    public InterstitialPages(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.ALMOST_THERE);
    }

    /**
     * Step 14 — waits out the loader.
     *
     * <p>The app runs a fixed ~6s sequence (250ms pre-roll plus ~1.9s per
     * checklist row) and then advances itself. Nothing is tapped, and no
     * specific screen is waited for: the caller may arrive while the loader is
     * already running, or after it has already handed over to step 15, and
     * insisting on catching it exactly would make this method fail on correct
     * behaviour. It waits for the loader to be <i>gone</i> instead, and reports
     * success either way.
     */
    @Step("Wait out the 'Almost there...' loader")
    public void waitOutAlmostThere() {
        Waits.untilTrue(Waits.LONG,
                "'Almost there...' loader to finish",
                () -> !OnboardingStep.ALMOST_THERE.isCurrentNow(driver));
    }

    /**
     * Step 15 — the coach video sequence.
     *
     * <p>The CTA exists in a disabled state while the videos play and becomes
     * enabled when the second clip finishes. Resolve it once, then watch only
     * that element's enabled state on a tight poll: repeating a full accessibility-tree
     * lookup after the button is already known to be present would add lookup
     * latency on top of the wait itself. The returned reference is clicked
     * immediately, without another lookup.
     *
     * <p>The shared bottom bar is never rendered on this step, so there is no
     * Continue to fall back on.
     */
    @Step("Advance past the Meet My Coach video")
    public void advancePastCoachVideo() {
        WebElement cta = Waits.enabledElement(
                driver,
                Locators.byTypeAndLabel("XCUIElementTypeButton", "Let's get started"),
                Waits.LONG,
                "coach video CTA (enabled when the clip finishes)");
        Waits.resilient(cta::click);
        waitForStep(OnboardingStep.LETS_START, Waits.MEDIUM);
    }

    /**
     * Step 16 — the intro card. It does render its own green Continue, distinct
     * from the shared bottom bar the app hides on this step.
     */
    @Step("Continue past the Let's get started card")
    public void advancePastLetsStart() {
        tapContinue();
        waitForStep(OnboardingStep.HEIGHT, Waits.MEDIUM);
    }

    /**
     * Step 23 — the thanks video, whose CTA reads "I'm Ready!".
     *
     * <p>That apostrophe is why every predicate in {@link Locators} goes through
     * a quoting helper — an unquoted interpolation here would produce a
     * malformed predicate and a locator that silently never matches. WDA
     * reports such a malformed predicate as "no such element" rather than as a
     * selector error, so the failure would look like a missing button.
     */
    @Step("Advance past the thanks video")
    public void advancePastThanks() {
        tap(Locators.byLabelContaining("I'm Ready"));
        waitForStep(OnboardingStep.TAP_AND_HOLD, Waits.MEDIUM);
    }
}