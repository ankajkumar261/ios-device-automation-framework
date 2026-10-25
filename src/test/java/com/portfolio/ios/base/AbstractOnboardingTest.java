package com.portfolio.ios.base;

import com.portfolio.ios.core.Artifacts;
import com.portfolio.ios.core.HealSuggester;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.flow.OnboardingFlow;
import com.portfolio.ios.model.Profile;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.util.Map;

/**
 * Session lifecycle for every onboarding test.
 *
 * <p>Two decisions worth calling out:
 *
 * <p><b>Reset before each test, not once per suite.</b> Onboarding is a
 * one-way door — once step 26 commits, {@code hasCompletedOnboarding} is true
 * and every later test would land on the home tab. Resetting per method is what
 * keeps tests independent and order-independent, which is the only reason they
 * can be run in parallel phases.
 *
 * <p><b>Capture on failure, always.</b> The teardown attaches a screenshot and
 * the accessibility page source to whichever test failed. Because this app
 * exposes no accessibility identifiers, that page source is the actual evidence
 * for any selector question that comes up later.
 */
public abstract class AbstractOnboardingTest {

    protected DriverFactory factory;
    protected IOSDriver driver;

    /** Overridable per test class; the default is the framework's own defaults. */
    protected Profile profile() {
        return Profile.builder().build();
    }

    @BeforeSuite(alwaysRun = true)
    public void publishEnvironment() {
        Allure.addAttachment("iosautomation-configuration", "text/plain",
                Config.describe().toString().indent(2), ".txt");
        Allure.addAttachment("iosautomation-profile", "text/plain", profile().toString(), ".txt");
    }

    /**
     * Tears down the previous session and resets the app to first-run state.
     *
     * <p>Order matters: the app must be terminated before its {@code UserDefaults}
     * are rewritten, because a live process keeps its own snapshot and would
     * otherwise overwrite the flag on its next write.
     */
    @BeforeMethod(alwaysRun = true)
    public void launchFreshApp() {
        if (factory != null) {
            factory.quit();
        }
        // Regression classes share one session; an onboarding test needs its own.
        AppSession.quit();
        DeviceAvailability.skipIfRefused();
        try {
            AppStateResetter.resetToFreshOnboarding();
            factory = new DriverFactory();
            driver = factory.driver();
        } catch (RuntimeException e) {
            DeviceAvailability.recordIfRefused(e);
            throw e;
        }
    }

    /**
     * Captures diagnostics on failure, then tears the session down.
     *
     * <p>Deliberately one method rather than two: TestNG's {@code @AfterMethod}
     * has no {@code priority} attribute, so two separate configuration methods
     * would run in an order the framework does not guarantee — and closing the
     * session before capturing would lose exactly the evidence the capture
     * exists to gather.
     */
    @AfterMethod(alwaysRun = true)
    public void captureFailureThenCloseSession(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE && driver != null) {
                Artifacts.capture(driver, safeTestName(result));
                Allure.addAttachment("failure-reason", "text/plain",
                        String.valueOf(result.getThrowable()), ".txt");
                HealSuggester.report(driver, result.getThrowable());
            }
        } finally {
            if (factory != null) {
                factory.quit();
                factory = null;
                driver = null;
            }
        }
    }

    @AfterSuite(alwaysRun = true)
    public void reportEnvironment() {
        Map<String, String> config = Config.describe();
        config.forEach((key, value) -> Allure.parameter(key, value));
    }

    protected OnboardingFlow flow() {
        return new OnboardingFlow(driver, factory, profile());
    }

    /**
     * Driver is gone by the time teardown runs, so pull the name from the result
     * rather than from the live session.
     */
    private String safeTestName(ITestResult result) {
        try {
            return result.getTestClass().getName() + "." + result.getName();
        } catch (RuntimeException e) {
            return "unknown-test";
        }
    }

    /** Exposed for tests that need to inspect lifecycle internals. */
    protected AllureLifecycle allure() {
        return Allure.getLifecycle();
    }
}
