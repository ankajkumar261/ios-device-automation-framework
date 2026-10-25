package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.model.OnboardingOptions;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

/**
 * Steps 21-22 — activity level, then the Apple Health permission offer.
 *
 * <p>Step 22 is unusual: it has no Continue button. The app advances only from
 * the completion handler of the HealthKit authorisation request, so the flow
 * cannot be driven by tapping anything — it must drive the system sheet, then
 * wait for the app to move on by itself.
 */
public final class ActivityHealthPage extends StepPage {

    public ActivityHealthPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.ACTIVITY);
    }

    /** Step 21. */
    @Step("Select activity level: {activity}")
    public void selectActivity(OnboardingOptions.Activity activity) {
        Waits.visible(driver, Locators.headingContaining("Tell us about your"), Waits.MEDIUM,
                "activity level heading");
        // Tap the card's title text rather than the composite Button: the Button's
        // accessibility label joins its title and description with a comma, while
        // the title StaticText is exact and stable.
        tap(Locators.byTypeAndLabel("XCUIElementTypeStaticText", activity.label()),
                "Select activity level");
        continueTo(OnboardingStep.APPLE_HEALTH);
    }

    /**
     * Step 22 — request Apple Health access and wait for the auto-advance.
     *
     * <p>The in-app Continue starts the authorization request; the iOS sheet
     * appears asynchronously afterward. Tapping a button labeled "Connect Apple
     * Health" does not work because that text is only the screen heading. If
     * authorization is already resolved without a sheet, the app may advance
     * directly and this simply observes that outcome.
     *
     * <p>Grant path is "Turn On All" then "Allow" (see
     * {@link SystemDialogPage#allowAppleHealth}): the sheet opens with every
     * category off, so enabling all before submitting is what makes the grant
     * meaningful. Any follow-up confirmation ("you can turn on categories
     * later…") is acknowledged either way — the app stays parked on step 22
     * until it is gone.
     *
     * @param grant whether to allow HealthKit access
     */
    @Step("Handle Apple Health prompt (grant={grant})")
    public void completeAppleHealth(boolean grant) {
        waitForStep(OnboardingStep.APPLE_HEALTH, Waits.MEDIUM);
        SystemDialogPage dialogs = new SystemDialogPage(driver, factory);

        // Quick probe: on the normal path no sheet is up yet and Continue must
        // be tapped. SHORT (8s) here would stall every run; QUICK (3s) is enough
        // to catch the rare case where the sheet is already showing.
        if (!dialogs.isDialogShowing(Waits.QUICK)) {
            tap(Locators.continueButton(), "Request Apple Health authorization");
        }

        // The sheet is presented asynchronously by SpringBoard, normally within
        // 1-3s. DIALOG (10s) replaces the previous MEDIUM (20s, actually 40s
        // with the old double-timeout isDialogShowing) — still generous for a
        // loaded simulator, without doubling the failure wait.
        if (dialogs.isDialogShowing(Waits.DIALOG)) {
            if (grant) {
                dialogs.allowAppleHealth();
            } else {
                dialogs.deny();
            }
            dialogs.acknowledgeRemainingAlert();
        }

        // No tap here on purpose — the app advances itself once the
        // authorisation request completes, regardless of the outcome. THANKS
        // follows within seconds; LONG (90s, meant for the AI backend on step
        // 25) would only inflate the failure wait, so MEDIUM is used.
        waitForStep(OnboardingStep.THANKS, Waits.MEDIUM);
    }
}