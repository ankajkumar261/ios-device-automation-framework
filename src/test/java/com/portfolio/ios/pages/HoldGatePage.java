package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * Step 24 — the press-and-hold gate that unlocks plan generation.
 *
 * <p>This is the only step a plain {@code click()} cannot pass. The hold is
 * wired to a {@code DragGesture(minimumDistance: 0)} that schedules a commit
 * after a fixed 1.3s and <i>cancels it if the finger lifts early</i>. A normal
 * tap therefore does nothing at all — it registers a momentary touch that is
 * released before the timer, and the step silently stays put.
 *
 * <p>The hold issued here is deliberately longer than the app's own threshold
 * so that scheduling jitter on a loaded CI machine does not land just under the
 * commit time and fail the test intermittently.
 *
 * <p>On real hardware the effective threshold is higher than 1.3s: the app
 * wakes its Core Haptics engine before starting the timer. See
 * {@link #holdToUnlock(long)} for how retries absorb that.
 */
public final class HoldGatePage extends StepPage {

    /** The app's own commit threshold, per {@code startHoldSequence}. */
    public static final Duration APP_COMMIT_THRESHOLD = Duration.ofMillis(1300);

    public HoldGatePage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.TAP_AND_HOLD);
    }

    /**
     * Holds the gate open and waits for the app to reach the plan loader.
     *
     * <p>Accepts the privacy consent first: the hold does nothing while the
     * "Please provide consent to continue" caption is showing, so holding
     * without checking the box would wait out the full timeout for a commit
     * the app will never make.
     *
     * @param holdMillis total hold duration; must exceed
     *                   {@link #APP_COMMIT_THRESHOLD} with margin
     */
    @Step("Press and hold the gate for {holdMillis}ms")
    public void holdToUnlock(long holdMillis) {
        if (holdMillis <= APP_COMMIT_THRESHOLD.toMillis()) {
            throw new IllegalArgumentException(
                    "holdMillis=" + holdMillis + " leaves no margin over the app's "
                            + APP_COMMIT_THRESHOLD.toMillis() + "ms commit threshold");
        }

        acceptPrivacyConsent();

        // Press the flame circle itself, not the caption below it: the gesture
        // recogniser sits on the flame, and a hold on the "TAP & HOLD"
        // StaticText ~100pt beneath it never starts the app's timer.
        WebElement gate = Waits.visible(driver,
                Locators.byTypeAndLabel("XCUIElementTypeImage", "FireIcon"),
                Waits.MEDIUM, "tap-and-hold flame gate");

        pressAndHold(gate, Duration.ofMillis(holdMillis));

        // The Actions hold did not commit (WDA may split press/pause into
        // separate gestures the app's DragGesture ignores). Retry natively
        // via mobile: touchAndHold, which presses one continuous touch, and
        // hold one app threshold longer on each retry.
        //
        // Why longer: the app's 1.3s timer starts only after onChanged has
        // woken its Core Haptics engine, synchronously on the main thread.
        // The simulator has no haptics hardware, so there the timer starts at
        // touch-down and the first hold commits. On an iPhone the engine wake
        // delays the timer — measured on iPhone 15 Pro / iOS 27, a 2.0s hold
        // never commits and a 3.0s hold does — so a hold sized for the
        // simulator lifts before the timer fires and the app cancels it.
        // Escalating only after an observed miss keeps simulator runs as fast
        // as before; the step change, not the duration, is what is trusted.
        for (int attempt = 1; attempt <= 2 && !leftHoldGate(); attempt++) {
            WebElement retry = Waits.visible(driver,
                    Locators.byTypeAndLabel("XCUIElementTypeImage", "FireIcon"),
                    Waits.SHORT, "tap-and-hold flame gate (retry " + attempt + ")");
            Duration longer = Duration.ofMillis(
                    holdMillis + attempt * APP_COMMIT_THRESHOLD.toMillis());
            com.portfolio.ios.core.Gestures.touchAndHold(driver, retry, longer);
        }

        // Verified by advancing, not by trusting the gesture landed.
        waitForStep(OnboardingStep.CREATING_PLAN, Waits.LONG);
    }

    /** True once the app has moved off the hold gate (commit accepted). */
    private boolean leftHoldGate() {
        return Waits.untilQuietly(Duration.ofSeconds(8),
                () -> !OnboardingStep.TAP_AND_HOLD.isCurrentNow(driver));
    }

    /**
     * Checks the "I have read and agree to the Privacy Policy…" box.
     *
     * <p>Step 24's only Button is the checkbox itself (SF symbol
     * {@code square} unchecked, {@code checkmark.square.*} checked); the
     * consent copy and the "Privacy Policy" link around it are StaticText/Link
     * and must not be tapped — the link would navigate away to the policy.
     * No-op when the box is already checked, so re-runs never uncheck it.
     */
    @Step("Accept the privacy consent")
    public void acceptPrivacyConsent() {
        // Step 24 slides in with a spring transition, and its heading is on
        // screen before the move ends. A tap on the 19pt checkbox mid-slide
        // lands where the box was, not where it is, and is lost (observed: the
        // box stayed unchecked after a tap that reported success). So wait for
        // the box to stop moving, tap, and verify; re-read the state before any
        // retry so a slow-to-report tap is never toggled back off.
        org.openqa.selenium.By checkboxBy =
                Locators.byTypeAndLabelContaining("XCUIElementTypeButton", "square");
        for (int attempt = 1; attempt <= 3; attempt++) {
            Waits.visible(driver, checkboxBy, Waits.MEDIUM, "privacy consent checkbox");
            waitUntilSettled(checkboxBy);
            WebElement checkbox = driver.findElement(checkboxBy);
            if (isChecked(checkbox)) {
                return;
            }
            Waits.resilient(checkbox::click);
            if (Waits.untilQuietly(Duration.ofSeconds(3), () ->
                    !isConsentCaptionShowing() || isCheckedNow())) {
                return;
            }
        }
        throw new Waits.WaitTimeout(
                "privacy checkbox tap did not check the box after 3 attempts — the hold gate "
                        + "stays disabled while 'Please provide consent to continue' is showing",
                Waits.SHORT, null);
    }

    /**
     * Waits until the element's frame reads the same twice in a row. Re-finds
     * it each read, since SwiftUI may rebuild it. Never fails: if it does not
     * settle in time, the caller's tap-and-verify loop still decides.
     */
    private void waitUntilSettled(org.openqa.selenium.By by) {
        org.openqa.selenium.Rectangle[] last = {null};
        try {
            Waits.until(Waits.SHORT, "privacy checkbox to stop moving", () -> {
                org.openqa.selenium.Rectangle now = driver.findElement(by).getRect();
                boolean settled = now.equals(last[0]);
                last[0] = now;
                return settled ? now : null;
            }, Duration.ofMillis(150));
        } catch (Waits.WaitTimeout stillMoving) {
            // Tap anyway; the verification after the tap is what counts.
        }
    }

    /** Checked state of a resolved checkbox element. */
    private boolean isChecked(WebElement checkbox) {
        String name = Waits.resilientGet(() -> checkbox.getAttribute("name"));
        return name != null && name.toLowerCase().contains("checkmark");
    }

    /** Re-resolves the checkbox (SwiftUI rebuilds it on tap) and reads its state. */
    private boolean isCheckedNow() {
        try {
            for (WebElement button : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
                try {
                    String name = button.getAttribute("name");
                    if (name != null && name.toLowerCase().contains("square")
                            && button.isDisplayed()) {
                        return name.toLowerCase().contains("checkmark");
                    }
                } catch (RuntimeException transientFailure) {
                    // Tree mid-animation; treat as unchecked on this pass.
                }
            }
        } catch (RuntimeException transientFailure) {
            // Whole-tree read raced a rebuild; outer wait polls again.
        }
        return false;
    }

    private boolean isConsentCaptionShowing() {
        return Waits.isPresent(driver, Locators.byLabelContaining("Please provide consent"));
    }
}