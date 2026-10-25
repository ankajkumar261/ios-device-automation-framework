package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

/**
 * Steps 25-26 — the AI plan loader and the final call to action.
 *
 * <p><b>These two steps are the suite's only non-deterministic surface.</b>
 * Step 25 waits on a live request to the app's production backend, which
 * proxies a meal-plan generation call to a third-party model. The host is
 * hardcoded to {@code .production} in {@code AppConfig}, so there is no test
 * environment to point at and no stub seam.
 *
 * <p>Consequences, stated plainly rather than papered over:
 * <ul>
 *   <li>step 25's wait is generous ({@link Waits#AI_BACKEND}) and the test that
 *       covers it is grouped as {@code ai} so it can be excluded from the fast
 *       inner loop;</li>
 *   <li>the generated plan's <i>content</i> is never asserted — only that the
 *       app reaches "Your Plan is Ready!", which is the part this framework
 *       actually owns.</li>
 * </ul>
 */
public final class PlanRevealPage extends StepPage {

    public PlanRevealPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.CREATING_PLAN);
    }

    /**
     * Step 25 — waits for the loader to finish and the plan to be revealed.
     *
     * <p>Nothing is tapped. The app advances itself once the generation request
     * resolves.
     */
    @Step("Wait for the AI plan to be generated")
    public void waitForPlanGeneration() {
        Waits.untilTrue(Waits.MEDIUM,
                "'Creating Your Plan…' loader to appear",
                () -> isVisible(step.markerLocator(), Waits.SHORT));

        waitForStep(OnboardingStep.PLAN_REVEAL, Waits.AI_BACKEND);
    }

    /**
     * Step 26 — verifies the revealed plan, then finishes onboarding.
     *
     * @return true once the app has committed and left onboarding
     */
    @Step("Finish onboarding")
    public boolean finishOnboarding() {
        Waits.visible(driver, Locators.byLabelContaining("Your Plan is Ready"), Waits.MEDIUM,
                "plan-ready heading");

        // Present but never asserted: the daily-calorie and BMI figures are
        // computed server-side, so pinning them would make this a test of the
        // backend rather than of the flow.
        tap(Locators.byLabelContaining("Start My Journey"));

        Waits.untilTrue(Waits.LONG, "onboarding to complete",
                () -> !isVisible(Locators.byLabelContaining("Your Plan is Ready"), Waits.SHORT));

        // Home's first appearance after onboarding asks for notification
        // permission when it is still undetermined. On hardware every session
        // is a fresh install (fullReset), so the prompt is always there; a
        // reused simulator install answered it long ago, so there this is a
        // short negative probe. Declined, matching the profile's privacy-first
        // Apple Health default, so the app is left on a usable Home screen.
        new SystemDialogPage(driver, factory).declineSystemAlertIfPresent(Waits.QUICK);
        return true;
    }
}