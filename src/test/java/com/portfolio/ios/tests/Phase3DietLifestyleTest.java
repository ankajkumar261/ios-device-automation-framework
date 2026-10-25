package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.model.OnboardingOptions;
import com.portfolio.ios.model.Profile;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Phase 3 — diet through the closing benefit card (steps 8-13).
 *
 * <p>The widest phase in the flow: two picker wheels plus four multi-select
 * chip screens. Multi-select is the part worth isolating, because the app treats
 * an empty selection as valid on every one of those screens, so a test that
 * only checks "did it advance" would pass even if every tap missed.
 */
@Story("Onboarding: diet and lifestyle")
public class Phase3DietLifestyleTest extends AbstractOnboardingTest {

    @Override
    protected Profile profile() {
        return Profile.builder()
                .diet(OnboardingOptions.Diet.VEGETARIAN)
                .eatingWindow(9, 21)
                .healthConcerns(List.of(
                        OnboardingOptions.HealthConcern.HIGH_CHOLESTEROL,
                        OnboardingOptions.HealthConcern.IBS))
                .commitments(List.of(
                        OnboardingOptions.Commitment.TRACK_MY_CALORIES,
                        OnboardingOptions.Commitment.EAT_LESS_SUGAR))
                .struggles(List.of(OnboardingOptions.Struggle.LATE_NIGHT_SNACKING))
                .build();
    }

    @Test(groups = {"phase3", "fast"})
    @Description("Diet, eating window, and all three multi-select screens advance")
    public void completesDietAndLifestyle() {
        flow().runIntro();
        flow().runProfileBasics();
        flow().expectStep(OnboardingStep.DIET);

        flow().runDietLifestyle();
        // Step 15, not step 14: 14 is the self-advancing loader that is already
        // gone by the time this polls, so it cannot be an assertion target.
        flow().expectStep(OnboardingStep.MEET_COACH);
    }

    @Test(groups = {"phase3", "fast"}, dependsOnMethods = "completesDietAndLifestyle")
    @Description("An empty multi-select is still a valid submission")
    public void emptySelectionsStillAdvance() {
        // Every one of these screens accepts an empty answer. A suite that only
        // ever submits non-empty lists would never notice if the taps stopped
        // working, because the Continue would keep advancing anyway.
        flow().runIntro();
        flow().runProfileBasics();

        var page = new com.portfolio.ios.pages.DietLifestylePage(driver, factory);
        page.selectDiet(OnboardingOptions.Diet.OMNIVORE);
        page.setEatingWindow(8, 20);
        page.selectHealthConcerns(List.of());
        page.selectCommitments(List.of());
        page.selectStruggles(List.of());
        page.advancePastBenefits();

        flow().expectStep(OnboardingStep.MEET_COACH);
    }
}
