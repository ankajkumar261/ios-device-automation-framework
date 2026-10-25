package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.flow.OnboardingFlow;
import com.portfolio.ios.model.Profile;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 6 — activity level and the Apple Health offer (steps 21-22).
 *
 * <p>Steps 21-22, kept out of the full-flow test because they touch system UI.
 * HealthKit's permission sheet lives outside the app's hierarchy, and the app
 * advances from step 22 only once that sheet has resolved — so this phase
 * exercises the dialog handling in isolation, where a failure is unambiguous.
 */
@Story("Onboarding: activity and health")
public class Phase6ActivityHealthTest extends AbstractOnboardingTest {

    @Override
    protected Profile profile() {
        return Profile.builder().connectAppleHealth(false).build();
    }

    /**
     * Restores the HealthKit sheet before any test in this class runs.
     *
     * <p>Answering the prompt once records the decision in the simulator's
     * Health store permanently, so without this every later run would take the
     * "already resolved, no sheet" shortcut — and this class would stop testing
     * the sheet interaction that is its entire reason to exist. The reboot this
     * costs (tens of seconds, once per class) is the price of deterministic
     * coverage here.
     */
    @BeforeClass(alwaysRun = true)
    public void restoreHealthKitPrompt() {
        assertTrue(AppStateResetter.resetHealthKitAuthorization(),
                "could not reset HealthKit authorization; the simulator may not have "
                        + "rebooted, so abort rather than run sheet tests against a dead device");
    }

    private void runToActivityStep() {
        runToActivityStep(flow());
    }

    private void runToActivityStep(OnboardingFlow target) {
        target.runIntro();
        target.runProfileBasics();
        target.runDietLifestyle();
        target.runInterstitials();
        target.runBodyMetrics();
        target.expectStep(OnboardingStep.ACTIVITY);
    }

    /**
     * Reboots the simulator for a second fresh HealthKit sheet and relaunches
     * the app on a new driver.
     *
     * <p>Needed because the deny test above already consumes the
     * {@code @BeforeClass} reset: answering once records the decision in the
     * simulator's Health store, so a second sheet test without a second reset
     * would take the "already resolved, no sheet" shortcut and never exercise
     * Turn On All at all. The reboot costs tens of seconds; it runs only for
     * the grant path, where the sheet interaction is the point.
     */
    private OnboardingFlow relaunchWithFreshHealthStore(Profile grantProfile) {
        if (factory != null) {
            factory.quit();
            factory = null;
            driver = null;
        }
        assertTrue(AppStateResetter.resetHealthKitAuthorization(),
                "could not reset HealthKit authorization for the grant path; abort rather "
                        + "than run Turn On All tests against an already-resolved store");
        AppStateResetter.resetToFreshOnboarding();
        factory = new DriverFactory();
        driver = factory.driver();
        return new OnboardingFlow(driver, factory, grantProfile);
    }

    @Test(groups = {"phase6", "fast"})
    @Description("Declining Apple Health still advances to the thanks screen")
    public void decliningHealthStillAdvances() {
        // The important case: the app's advance is driven by the authorisation
        // completion handler, not by a button, so a denial must still land on
        // step 23. A test that only ever grants access would miss a regression
        // that strands the user on this screen forever.
        runToActivityStep();
        flow().runActivityHealth();
        flow().expectStep(OnboardingStep.THANKS);
    }

    @Test(groups = {"phase6", "fast"}, dependsOnMethods = "decliningHealthStillAdvances")
    @Description("No system dialog is left on screen when the phase finishes")
    public void noDialogLeftBehind() {
        runToActivityStep();
        flow().runActivityHealth();

        assertFalse(new com.portfolio.ios.pages.SystemDialogPage(driver, factory)
                        .isDialogShowing(Waits.QUICK),
                "a system permission dialog is still showing after the Apple Health step");
    }

    @Test(groups = {"phase6", "fast"},
            dependsOnMethods = {"decliningHealthStillAdvances", "noDialogLeftBehind"})
    @Description("Granting via Turn On All then Allow advances and continues past thanks")
    public void grantingHealthViaTurnOnAllAdvancesAndContinues() {
        // Fresh store so the sheet actually appears; otherwise this would pass
        // via the already-resolved shortcut without ever tapping Turn On All.
        Profile grantProfile = Profile.builder().connectAppleHealth(true).build();
        OnboardingFlow grantFlow = relaunchWithFreshHealthStore(grantProfile);

        runToActivityStep(grantFlow);
        // Turn On All -> Allow happens inside; any follow-up confirmation is
        // acknowledged there, and step 22 auto-advances to thanks.
        grantFlow.runActivityHealth();
        grantFlow.expectStep(OnboardingStep.THANKS);

        assertFalse(new com.portfolio.ios.pages.SystemDialogPage(driver, factory)
                        .isDialogShowing(Waits.QUICK),
                "a system permission dialog is still showing after granting Apple Health");

        // Continue automatically with the remaining onboarding steps (without
        // entering the AI-backend plan generation): past the thanks video to
        // the hold gate. This proves the grant didn't strand the flow on step
        // 22/23 and the app is still drivable afterward.
        new com.portfolio.ios.pages.InterstitialPages(driver, factory).advancePastThanks();
        grantFlow.expectStep(OnboardingStep.TAP_AND_HOLD);
        grantFlow.captureCurrentScreen("phase6-grant-continued-to-hold-gate");
    }
}
