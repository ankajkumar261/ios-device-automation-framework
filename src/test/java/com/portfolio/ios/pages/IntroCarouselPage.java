package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

/**
 * Steps 0-5 — the six full-bleed video intros.
 *
 * <p>All six are the same shape: a looping/one-shot video, a headline, and a
 * green Continue. The app's own {@code ctaTitle} returns "Get Started" for step
 * 0, but step 0 never renders the global bottom bar — it draws its own CTA from
 * {@code AppStrings.Common.continueButton}, so the on-screen label really is
 * "Continue". Confirmed against a live accessibility-tree capture.
 */
public final class IntroCarouselPage extends StepPage {

    public IntroCarouselPage(IOSDriver driver, DriverFactory factory, OnboardingStep step) {
        super(driver, factory, step);
    }

    /** Steps 0-5 share one advance primitive; kept as a single named method. */
    @Step("Advance intro carousel")
    public void advanceIntro(OnboardingStep next) {
        continueTo(next);
    }
}