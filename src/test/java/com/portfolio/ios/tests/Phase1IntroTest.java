package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.flow.OnboardingFlow;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/**
 * Phase 1 — the six video intros (steps 0-5).
 *
 * <p>The cheapest phase to run, so it doubles as the canary for the carousel's
 * shared Continue button.
 */
@Story("Onboarding: intro")
public class Phase1IntroTest extends AbstractOnboardingTest {

    @Test(groups = {"phase1", "fast"})
    @Description("All six carousel panels advance and land on the gender step")
    public void carouselAdvancesToGender() {
        flow().expectStep(OnboardingStep.WELCOME);
        flow().runIntro();
        flow().expectStep(OnboardingStep.GENDER);
    }

    @Test(groups = {"phase1", "fast"}, dependsOnMethods = "carouselAdvancesToGender")
    @Description("Every carousel panel is individually identifiable, one at a time")
    public void everyCarouselPanelIsRecognised() {
        // Proves OnboardingStep's markers match the real screens and not just the
        // first one. Marker drift is otherwise invisible until a much later phase
        // times out for no obvious reason. Advances one panel per iteration, so a
        // bad marker is reported against the screen that actually has it.
        OnboardingFlow flow = flow();
        for (OnboardingStep step : OnboardingFlow.carouselSteps()) {
            flow.expectStep(step);
            // Every panel is advanced, including the last: skipping the final
            // Continue would leave the app on the coach panel and make the
            // trailing assertion below look like a marker failure.
            flow.advanceOnePanel(step);
        }
        flow.expectStep(OnboardingStep.GENDER);
    }
}
