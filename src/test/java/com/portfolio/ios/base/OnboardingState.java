package com.portfolio.ios.base;

import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.pages.Locators;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Allure;

/**
 * Whether the launched app still needs onboarding, read from what it shows:
 * the app opens on onboarding until {@code hasCompletedOnboarding} is set,
 * and on Home after ({@code IosNutritionApp.swift:76}). Reading the screen works on
 * a real device too, where the flag itself cannot be read from the Mac.
 */
public enum OnboardingState {
    ONBOARDING_REQUIRED,
    ONBOARDING_ALREADY_COMPLETED;

    public static OnboardingState detect(IOSDriver driver) {
        OnboardingState state = Waits.until(Waits.LONG, "Home or the first onboarding screen", () -> {
            if (Waits.isPresent(driver, Locators.forceUpdateCover())) {
                throw new AssertionError("Blocked by the force-update screen ('Update Required')");
            }
            if (Waits.isPresent(driver, Locators.homeCalendarButton())) {
                return ONBOARDING_ALREADY_COMPLETED;
            }
            if (Waits.isPresent(driver, OnboardingStep.WELCOME.markerLocator())) {
                return ONBOARDING_REQUIRED;
            }
            return null;
        });
        Allure.step("Onboarding state: " + state);
        return state;
    }
}
