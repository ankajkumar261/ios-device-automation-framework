package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.fixtures.AppStateResetter;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 7 — the press-and-hold gate (step 24), in isolation.
 *
 * <p>Kept separate from the full-flow test because the gate is the one step a
 * plain click cannot pass: the app schedules its commit at 1.3s and cancels it
 * if the finger lifts early, so a too-short hold fails silently by never
 * advancing. Testing it alone makes that a clear failure instead of a timeout
 * twenty steps later.
 */
@Story("Onboarding: hold gate")
public class Phase7HoldGateTest extends AbstractOnboardingTest {

    @Test(groups = {"phase7", "ruler"})
    @Description("A hold past the app's threshold unlocks the plan loader")
    public void holdUnlocksPlanGeneration() {
        // Walks the whole flow to reach the gate; there is no shortcut, because
        // the app only offers this screen after a complete profile.
        flow().runIntro();
        flow().runProfileBasics();
        flow().runDietLifestyle();
        flow().runInterstitials();
        flow().runBodyMetrics();
        flow().runActivityHealth();
        // Step 23 (thanks video) sits between Apple Health and the gate.
        new com.portfolio.ios.pages.InterstitialPages(driver, factory).advancePastThanks();

        new com.portfolio.ios.pages.HoldGatePage(driver, factory).holdToUnlock(profile().holdMillis());
        flow().captureCurrentScreen("hold-gate-unlocked");
    }

    @Test(groups = {"phase7", "fast"})
    @Description("A hold below the app's commit threshold is rejected as too short")
    public void tooShortHoldIsRejectedUpFront() {
        // The page object refuses a sub-threshold hold rather than performing it
        // and waiting to time out. This asserts that guard exists, so a future
        // profile edit cannot quietly reintroduce a hang.
        var tooShort = new com.portfolio.ios.pages.HoldGatePage(driver, factory);
        // A hold at exactly the app's commit threshold has no margin, so the page
        // object must reject it up front rather than perform it and time out.
        // (TestNG's expectThrows takes no message argument, hence the comment.)
        org.testng.Assert.expectThrows(IllegalArgumentException.class,
                () -> tooShort.holdToUnlock(
                        com.portfolio.ios.pages.HoldGatePage.APP_COMMIT_THRESHOLD.toMillis()));
    }

    @Test(groups = {"phase7", "fast"})
    @Description("Finishing onboarding commits the flag so the app skips onboarding")
    public void completionCommitsTheOnboardingFlag() {
        if (com.portfolio.ios.core.Config.isReal()) {
            throw new org.testng.SkipException(
                    "simulator-only: UserDefaults is unreadable on hardware");
        }
        assertFalse(Boolean.TRUE.equals(AppStateResetter.isOnboardingComplete()),
                "test should start from a pre-onboarding state");

        AppStateResetter.markOnboardingComplete();
        assertTrue(Boolean.TRUE.equals(AppStateResetter.isOnboardingComplete()),
                "marking onboarding complete must persist the flag, otherwise the next "
                        + "test would silently re-run onboarding instead of the main tab");
    }
}
