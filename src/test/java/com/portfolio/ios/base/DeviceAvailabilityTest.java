package com.portfolio.ios.base;

import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Which errors mean "the phone gave no session at all", so the rest of the run skips. */
@Story("Harness: device fail-fast")
public class DeviceAvailabilityTest {

    @Test(groups = {"config", "fast"})
    @Description("A refused or missing device counts, even when wrapped")
    public void refusedSessionCounts() {
        assertTrue(DeviceAvailability.isSessionRefused(new SessionNotCreatedException(
                "The iPhone refused UI automation (XCTDaemonErrorDomain 41).")));
        assertTrue(DeviceAvailability.isSessionRefused(new IllegalStateException("setup failed",
                new IllegalStateException("No iPhone is connected by USB."))));
    }

    @Test(groups = {"config", "fast"})
    @Description("A failure inside a working session does not")
    public void failureInsideSessionDoesNot() {
        assertFalse(DeviceAvailability.isSessionRefused(new AssertionError("'AI Coach' title not shown")));
        assertFalse(DeviceAvailability.isSessionRefused(new WebDriverException("socket hang up")));
    }
}
