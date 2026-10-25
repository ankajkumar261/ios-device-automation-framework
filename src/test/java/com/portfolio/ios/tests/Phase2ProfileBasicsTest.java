package com.portfolio.ios.tests;

import com.portfolio.ios.base.AbstractOnboardingTest;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.model.OnboardingOptions;
import com.portfolio.ios.model.Profile;
import com.portfolio.ios.pages.ProfileBasicsPage;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Phase 2 — sex and birth year (steps 6-7).
 *
 * <p>The first phase to use real widgets: a chip grid and a {@code UIPickerView}.
 * Worth its own test because the birth-year wheel is the first element in the
 * app that is not a plain label match.
 */
@Story("Onboarding: profile basics")
public class Phase2ProfileBasicsTest extends AbstractOnboardingTest {

    @Override
    protected Profile profile() {
        return Profile.builder()
                .sex(OnboardingOptions.Sex.MALE)
                .birthYear(1990)
                .build();
    }

    @Test(groups = {"phase2", "fast"})
    @Description("Sex and birth year are recorded and advance to the diet step")
    public void recordsSexAndBirthYear() {
        flow().runIntro();
        flow().expectStep(OnboardingStep.GENDER);

        flow().runProfileBasics();
        flow().expectStep(OnboardingStep.DIET);
    }

    @Test(groups = {"phase2", "fast"}, dependsOnMethods = "recordsSexAndBirthYear")
    @Description("The birth-year wheel lands on the requested year, not an adjacent one")
    public void birthYearWheelLandsExactly() {
        flow().runIntro();

        // Driven step by step rather than via runProfileBasics(), so the page
        // instance whose read-back is asserted is the one that set the wheel.
        ProfileBasicsPage page = new ProfileBasicsPage(driver, factory);
        page.selectSex(profile().sex());
        page.setBirthYear(profile().birthYear());

        // Asserts against what the wheel actually settled on, not against the
        // value we asked for. SwiftUI wheels here expose no row text, so the
        // selection is driven by nudging one row at a time — which lands on an
        // adjacent year the moment a nudge is dropped, and that would quietly
        // change every downstream answer.
        assertTrue(page.lastSelectedYear().contains(String.valueOf(profile().birthYear())),
                "birth-year wheel settled on '" + page.lastSelectedYear()
                        + "' instead of " + profile().birthYear());
        flow().expectStep(OnboardingStep.DIET);
    }
}
