package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.flow.OnboardingFlow;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/**
 * Phase 4 — the loader and the two video screens (steps 14-16).
 *
 * <p>Nothing here can be driven by tapping Continue. Step 14 advances on a
 * fixed timer, step 15's CTA is bespoke, and step 16 hides the shared bottom
 * bar entirely — three different reasons for the same symptom, which is why
 * this phase is worth its own test rather than being buried in a full-flow run.
 */
@Story("Onboarding: interstitials")
public class Phase4InterstitialsTest extends AbstractOnboardingTest {

    /** Reaches the end of phase 3, which is where this phase begins. */
    private void runUpToAlmostThere() {
        flow().runIntro();
        flow().runProfileBasics();
        flow().runDietLifestyle();
        // Step 14 is the self-advancing loader, already gone by the time this
        // polls, so the checkpoint is "past the loader" rather than "on it".
        flow().expectStepOrBeyond(OnboardingStep.ALMOST_THERE);
    }

    @Test(groups = {"phase4", "fast"}, dependsOnGroups = "phase3")
    @Description("The self-advancing loader, the coach video, and the intro card all pass")
    public void passesLoaderAndVideos() {
        runUpToAlmostThere();
        flow().runInterstitials();
        flow().expectStep(OnboardingStep.HEIGHT);
    }

    @Test(groups = {"phase4", "fast"}, dependsOnMethods = "passesLoaderAndVideos")
    @Description("Step 14 advances without any interaction")
    public void loaderAdvancesOnItsOwn() {
        runUpToAlmostThere();

        // Waits, never taps. If this ever needed a tap, the wait would burn its
        // full timeout and the failure would point at step 15 rather than at
        // the real cause, which is that nothing advances step 14.
        new com.portfolio.ios.pages.InterstitialPages(driver, factory).waitOutAlmostThere();
        flow().expectStep(OnboardingStep.MEET_COACH);
    }

    @Test(groups = {"phase4", "fast"}, dependsOnGroups = "phase4")
    @Description("Every phase boundary is contiguous, so no phase skips a screen")
    public void phaseBoundariesAreContiguous() {
        OnboardingFlow.Phase[] phases = OnboardingFlow.Phase.values();
        for (int i = 1; i < phases.length; i++) {
            org.testng.Assert.assertEquals(phases[i].firstStep(), phases[i - 1].lastStep() + 1,
                    "gap between " + phases[i - 1] + " and " + phases[i]);
        }
    }
}
