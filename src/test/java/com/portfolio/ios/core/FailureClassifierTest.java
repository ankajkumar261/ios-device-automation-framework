package com.portfolio.ios.core;

import com.portfolio.ios.core.FailureClassifier.Kind;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SessionNotCreatedException;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;

/** How failures are sorted for triage — from real failure messages seen in this suite. */
@Story("Harness: failure triage")
public class FailureClassifierTest {

    @Test(groups = {"config", "fast"})
    @Description("A wait that timed out on an element is 'UI changed'")
    public void waitTimeoutIsUiChanged() {
        assertEquals(FailureClassifier.classify(new Waits.WaitTimeout("'AI Coach' title", Duration.ofSeconds(20), null)),
                Kind.UI_CHANGED);
        assertEquals(FailureClassifier.classify(new NoSuchElementException("no such element")), Kind.UI_CHANGED);
        assertEquals(FailureClassifier.classify(new AssertionError("AI INSIGHT banner never scrolled into view")),
                Kind.UI_CHANGED);
        assertEquals(FailureClassifier.classify(new AssertionError(
                "No elapsed clock on the fasting card: [Fasting in progress, ·, 16:8 protocol]")), Kind.UI_CHANGED);
    }

    @Test(groups = {"config", "fast"})
    @Description("A value that did not match is 'phealthcareble bug'")
    public void assertionIsPhealthcarebleBug() {
        assertEquals(FailureClassifier.classify(new AssertionError(
                "Remaining after logging 173 kcal expected [2061] but found [2234]")), Kind.PRegistryBLE_BUG);
    }

    @Test(groups = {"config", "fast"})
    @Description("Appium, device and configuration problems are 'environment', even when wrapped")
    public void sessionAndConfigProblemsAreEnvironment() {
        assertEquals(FailureClassifier.classify(new SessionNotCreatedException("xcodebuild failed with code 65")),
                Kind.ENVIRONMENT);
        assertEquals(FailureClassifier.classify(new IllegalStateException("setup failed",
                new IllegalStateException("No iPhone is connected by USB."))), Kind.ENVIRONMENT);
        assertEquals(FailureClassifier.classify(new org.openqa.selenium.WebDriverException(
                "Failed to read lockdown via appium-ios-remotexpc USBMUX path for '00008030-000A28101122802E': "
                        + "Timed out waiting for plist response after 5000ms")), Kind.ENVIRONMENT);
    }

    @Test(groups = {"config", "fast"})
    @Description("Anything else is 'other'")
    public void unknownIsOther() {
        assertEquals(FailureClassifier.classify(new IllegalArgumentException("bad argument")), Kind.OTHER);
    }
}
