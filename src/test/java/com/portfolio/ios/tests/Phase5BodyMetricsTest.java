package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.model.Profile;
import com.portfolio.ios.pages.BodyMetricsPage;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/**
 * Phase 5 — the three rulers and the pace slider (steps 17-20).
 *
 * <p>Where this framework is most likely to be flaky, and the phase that
 * benefits most from a dedicated test. The rulers are opaque SwiftUI canvases
 * driven only by a drag, so the page object closes the loop by reading the
 * numeric readout back. These tests exist to prove that loop converges rather
 * than to assert anything about the app.
 */
@Story("Onboarding: body metrics")
public class Phase5BodyMetricsTest extends AbstractOnboardingTest {

    @Override
    protected Profile profile() {
        // Deliberately not the defaults: the ruler loop is the interesting part
        // and needs a value far from the starting position, so a drag that moves
        // the wrong amount cannot accidentally land on target.
        return Profile.builder()
                .heightCm(184)
                .weightKg(92.5)
                .goalWeightKg(78)
                .build();
    }

    private void runToHeightStep() {
        flow().runIntro();
        flow().runProfileBasics();
        flow().runDietLifestyle();
        flow().runInterstitials();
        flow().expectStep(OnboardingStep.HEIGHT);
    }

    @Test(groups = {"phase5", "ruler"})
    @Description("Height, weight, goal weight, and pace all set and advance to activity")
    public void completesBodyMetrics() {
        runToHeightStep();
        flow().runBodyMetrics();
        flow().expectStep(OnboardingStep.ACTIVITY);
    }

    @Test(groups = {"phase5", "ruler"}, dependsOnMethods = "completesBodyMetrics")
    @Description("A large downward drag converges, not just a small nudge")
    public void largeDeltaConverges() {
        // The failure mode this guards is subtle: a single drag of the size the
        // first draft used is small enough to look like it moved the value while
        // actually being clamped by the screen edge, leaving the loop to converge
        // from the wrong place. Forcing a big delta exercises the iteration.
        runToHeightStep();

        BodyMetricsPage page = new BodyMetricsPage(driver, factory);
        page.setHeightCm(120);
        flow().expectStep(OnboardingStep.WEIGHT);
    }
}
