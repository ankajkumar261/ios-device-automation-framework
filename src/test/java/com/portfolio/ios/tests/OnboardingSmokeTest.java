package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.fixtures.AppStateResetter;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 0 — does the harness work at all?
 *
 * <p>Deliberately the smallest phealthcareble test. Every other test in the suite
 * depends on the driver attaching, the app launching into onboarding, and the
 * reset actually clearing state; when one of those is broken, running a
 * twenty-minute full-flow test tells you only that "onboarding failed". This one
 * isolates the plumbing in seconds and, just as usefully, verifies that the
 * reset is doing its job — a test that passes because the app happened to be in
 * a clean state would silently pass for the wrong reason afterwards.
 */
@Story("Harness")
public class OnboardingSmokeTest extends AbstractOnboardingTest {

    @Test(groups = {"smoke", "fast"})
    @Description("The app launches into onboarding step 0 after a reset")
    public void launchesIntoOnboardingFromFreshState() {
        assertFalse(
                Boolean.TRUE.equals(AppStateResetter.isOnboardingComplete()),
                "hasCompletedOnboarding should be false after reset, so the app must "
                        + "show onboarding rather than the main tab shell");

        flow().expectStep(OnboardingStep.WELCOME);
    }

    @Test(groups = {"smoke", "fast"})
    @Description("The very first Continue is tappable and moves to step 1")
    public void firstScreenAdvancesToStepOne() {
        flow().expectStep(OnboardingStep.WELCOME);
        flow().runIntro();
        flow().expectStep(OnboardingStep.GENDER);
    }

    @Test(groups = {"smoke", "fast"})
    @Description("The reset flag round-trips through the simulator's defaults store")
    public void resetWritesTheOnboardingFlag() {
        if (com.portfolio.ios.core.Config.isReal()) {
            throw new org.testng.SkipException(
                    "simulator-only: UserDefaults is unreadable on hardware");
        }
        AppStateResetter.markOnboardingComplete();
        assertTrue(Boolean.TRUE.equals(AppStateResetter.isOnboardingComplete()),
                "markOnboardingComplete() should make the flag readable as true");

        AppStateResetter.resetToFreshOnboarding();
        assertFalse(Boolean.TRUE.equals(AppStateResetter.isOnboardingComplete()),
                "resetToFreshOnboarding() should clear the flag again");
    }

    @Test(groups = {"smoke", "fast"})
    @Description("The 27-step phase map covers every step exactly once, in order")
    public void phaseMapCoversEveryStep() {
        // Guards the enum against accidental edits: a gap or overlap here would
        // let a phase silently skip a screen, and the symptom would only ever be
        // a timeout much later in the flow.
        var phases = com.portfolio.ios.flow.OnboardingFlow.Phase.values();
        int expectedNext = 0;
        for (var phase : phases) {
            assertEquals(phase.firstStep(), expectedNext,
                    "phase " + phase + " does not start where the previous one ended");
            expectedNext = phase.lastStep() + 1;
        }
        assertEquals(expectedNext, 27,
                "the phases must cover all 27 steps with none left over");
    }
}