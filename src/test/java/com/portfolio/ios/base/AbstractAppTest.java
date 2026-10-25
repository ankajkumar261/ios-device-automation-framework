package com.portfolio.ios.base;

import com.portfolio.ios.core.Artifacts;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.HealSuggester;
import com.portfolio.ios.fixtures.AppStateResetter;
import com.portfolio.ios.fixtures.BaselineSnapshot;
import com.portfolio.ios.fixtures.DeviceBaseline;
import com.portfolio.ios.flow.OnboardingFlow;
import com.portfolio.ios.model.Profile;
import com.portfolio.ios.pages.HomePage;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Base for every module that starts after onboarding (Home, food, water, …).
 *
 * <p><b>Onboarding runs once, not per class.</b> The first class of a run
 * (or of a new build or day) onboards once and saves the result as a
 * baseline — on the simulator {@link BaselineSnapshot}, on a real iPhone
 * {@link DeviceBaseline}; every class after that restores the baseline in
 * seconds and starts on a freshly onboarded Home. Whether onboarding is
 * needed is read from the app itself ({@link OnboardingState}). All classes
 * share one Appium session ({@link AppSession}); on a real device that
 * session reinstalls the app only when there is no baseline to restore.
 *
 * <p><b>Every test starts on Home.</b> {@link HomePage#open()} closes any
 * sheet a previous test left open. Classes whose tests each need untouched
 * data override {@link #restoreBaselineBeforeEachTest()}.
 */
public abstract class AbstractAppTest {

    protected DriverFactory factory;
    protected IOSDriver driver;
    /** Why class setup failed, if it did; every test is then skipped with it. */
    private Throwable setupFailure;
    /** True while the app is exactly as class setup left it — no test has run yet. */
    private boolean untouched;

    /** Profile used for onboarding; override per class if needed. */
    protected Profile profile() {
        return Profile.builder().build();
    }

    /**
     * Why this class cannot run on a real device, or null when it can.
     * Checked before onboarding, so a simulator-only class skips at once on
     * hardware instead of onboarding for nothing.
     */
    protected String simulatorOnlyReason() {
        return null;
    }

    /**
     * True for classes whose tests each need the freshly onboarded data (no
     * leftovers from the test before). The simulator then restores the
     * baseline before every test — a few seconds — instead of the test
     * cleaning up through the app.
     */
    protected boolean restoreBaselineBeforeEachTest() {
        return false;
    }

    /**
     * True when this test starts from the restored, freshly onboarded app
     * (per-test restore) — clean-up through the app is then unnecessary.
     */
    protected boolean testStartsFromBaseline() {
        return restoreBaselineBeforeEachTest();
    }

    @BeforeSuite(alwaysRun = true)
    public void publishEnvironment() {
        Allure.addAttachment("iosautomation-configuration", "text/plain",
                Config.describe().toString().indent(2), ".txt");
    }

    /**
     * Freshly onboarded app, Home on screen.
     *
     * <p>Captures evidence before rethrowing: if this fails, every test in the
     * class is skipped, and the screenshot is the only record of why.
     */
    @BeforeClass(alwaysRun = true)
    public void startOnboardedApp() {
        if (Config.isReal() && simulatorOnlyReason() != null) {
            setupFailure = new SkipException("Simulator-only: " + simulatorOnlyReason());
            throw (SkipException) setupFailure;
        }
        try {
            // Asking a phone that refused again would only repeat the refusal, class after class.
            DeviceAvailability.skipIfRefused();
        } catch (SkipException e) {
            setupFailure = e;
            throw e;
        }
        try {
            // A real device reinstalls only when there is no baseline to restore.
            factory = AppSession.factory(!baselineExists());
            driver = factory.driver();
            startFromBaseline();
            untouched = true;
        } catch (RuntimeException | AssertionError e) {
            setupFailure = e;
            DeviceAvailability.recordIfRefused(e);
            if (driver != null) {
                Artifacts.capture(driver, getClass().getSimpleName() + "-setup");
                HealSuggester.report(driver, e);
            }
            throw e;
        }
    }

    /**
     * Restores today's baseline for the installed build; without one, onboards
     * once — only if the app actually shows onboarding — and saves it.
     */
    private void startFromBaseline() {
        if (baselineExists()) {
            if (restoreBaselineAtClassStart()) {
                return;
            }
            // The restored app shows onboarding: it was reinstalled since the
            // baseline was saved (its preferences are gone). Start over.
            DeviceBaseline.discard();
            AppSession.quit();
            factory = AppSession.factory(true);
            driver = factory.driver();
        }
        AppStateResetter.wipeAppData();   // simulator; a real device was just reinstalled
        launch();
        if (OnboardingState.detect(driver) == OnboardingState.ONBOARDING_REQUIRED) {
            new OnboardingFlow(driver, factory, profile()).runAll();
        }
        home().waitUntilLoaded();
        boolean saved = Config.isReal() ? DeviceBaseline.save(driver) : BaselineSnapshot.save();
        if (!saved) {
            throw new IllegalStateException("Could not save the onboarded baseline to "
                    + (Config.isReal() ? DeviceBaseline.dir() : BaselineSnapshot.dir()));
        }
        relaunchApp();
    }

    private static boolean baselineExists() {
        return Config.isReal() ? DeviceBaseline.exists() : BaselineSnapshot.exists();
    }

    /**
     * Restores the baseline and launches; true when the app opens on Home.
     * A real device can only restore the app's data, not its preferences, so
     * an app reinstalled since the baseline opens on onboarding instead.
     */
    private boolean restoreBaselineAtClassStart() {
        restoreBaselineData();
        launch();
        if (OnboardingState.detect(driver) == OnboardingState.ONBOARDING_REQUIRED) {
            return false;
        }
        home().waitUntilLoaded();
        return true;
    }

    /** Puts the freshly onboarded data back and relaunches onto Home. */
    @Step("Restore the freshly onboarded app")
    protected HomePage restoreBaseline() {
        restoreBaselineData();
        return relaunchApp();
    }

    private void restoreBaselineData() {
        boolean restored = Config.isReal() ? DeviceBaseline.restore(driver) : BaselineSnapshot.restore();
        if (!restored) {
            throw new IllegalStateException("Could not restore the onboarded baseline from "
                    + (Config.isReal() ? DeviceBaseline.dir() : BaselineSnapshot.dir()));
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void returnToHome() {
        if (setupFailure instanceof SkipException skip) {
            throw skip;
        }
        if (setupFailure != null) {
            // alwaysRun still fires after a failed class setup. Skip with the
            // cause instead of letting every test fail on its own, slowly,
            // against a screen that is not Home.
            throw new SkipException("Class setup failed, so this test cannot start from Home: "
                    + setupFailure);
        }
        try {
            bringAppToFront();
            if (restoreBaselineBeforeEachTest() && !untouched) {
                restoreBaseline();
            } else {
                home().open();
            }
            untouched = false;
        } catch (RuntimeException | AssertionError e) {
            // A failure here skips the test, so @AfterMethod does not see a
            // FAILURE; capture the screen now or the evidence is lost.
            Artifacts.capture(driver, getClass().getSimpleName() + "-returnToHome");
            HealSuggester.report(driver, e);
            throw e;
        }
    }

    /**
     * Puts the app back in front when something else took over since the last
     * test: a notification banner the test tapped (TestFlight), Support handing
     * over to Mail, a system alert. Seen on the iPhone; one query when nothing
     * happened. A system alert is closed only through its Cancel button, so a
     * permission prompt is never answered here.
     */
    private void bringAppToFront() {
        String active = activeBundleId();
        if (active == null || Config.bundleId().equals(active)) {
            return;
        }
        System.err.println("[iosautomation] " + active + " was in front of the app; bringing the app back");
        try {
            Object buttons = driver.executeScript("mobile: alert", java.util.Map.of("action", "getButtons"));
            if (buttons instanceof java.util.List<?> labels && labels.contains("Cancel")) {
                driver.executeScript("mobile: alert", java.util.Map.of("action", "dismiss", "buttonLabel", "Cancel"));
            }
        } catch (RuntimeException noAlert) {
            // No system alert: only another app is in front.
        }
        driver.activateApp(Config.bundleId());
        com.portfolio.ios.core.Waits.untilTrue(com.portfolio.ios.core.Waits.SHORT, "the app back in front",
                () -> Config.bundleId().equals(activeBundleId()));
    }

    private String activeBundleId() {
        Object info = driver.executeScript("mobile: activeAppInfo");
        return info instanceof java.util.Map<?, ?> map && map.get("bundleId") != null
                ? String.valueOf(map.get("bundleId")) : null;
    }

    @AfterMethod(alwaysRun = true)
    public void captureOnFailure(ITestResult result) {
        if (result.getStatus() == ITestResult.FAILURE && driver != null) {
            Artifacts.capture(driver, result.getTestClass().getRealClass().getSimpleName()
                    + "." + result.getMethod().getMethodName());
            Allure.addAttachment("failure-reason", "text/plain",
                    String.valueOf(result.getThrowable()), ".txt");
            HealSuggester.report(driver, result.getThrowable());
        }
    }

    /** The shared session stays open for the next class; it closes after the suite. */
    @AfterClass(alwaysRun = true)
    public void closeSession() {
        factory = null;
        driver = null;
    }

    @AfterSuite(alwaysRun = true)
    public void closeSharedSession() {
        AppSession.quit();
    }

    protected HomePage home() {
        return new HomePage(driver, factory);
    }

    /**
     * Launches the (terminated) app with the same arguments a fresh session
     * gives it — English, review prompt off — and waits for Home. A plain
     * {@code activateApp} after a terminate would drop those arguments.
     */
    protected HomePage relaunchApp() {
        // "mobile: launchApp" only brings a running app to the front, so
        // terminate first: a relaunch must start a new process (and drop any
        // launch arguments of the previous one).
        driver.executeScript("mobile: terminateApp", java.util.Map.of("bundleId", Config.bundleId()));
        launch();
        return home().waitUntilLoaded();
    }

    /**
     * Terminates and relaunches with extra launch arguments. "-key value"
     * pairs land in the app's UserDefaults argument domain for that launch
     * only: they override what the app reads without writing anything, and
     * the next plain {@link #relaunchApp()} drops them. Works on a real
     * device too, unlike editing the app's preferences.
     */
    protected HomePage relaunchAppWith(String... extraArguments) {
        driver.executeScript("mobile: terminateApp", java.util.Map.of("bundleId", Config.bundleId()));
        launch(extraArguments);
        return home().waitUntilLoaded();
    }

    /**
     * Launch arguments that make the app's daily limit for {@code bucket}
     * ("scan" or "general") read as already used up today
     * ({@code Core/DailyAPILimiter.swift}).
     */
    protected static String[] dailyLimitReached(String bucket, int limit) {
        return new String[]{"-DailyAPILimiter.count." + bucket, String.valueOf(limit),
                "-DailyAPILimiter.date." + bucket, java.time.LocalDate.now().toString()};
    }

    private void launch(String... extraArguments) {
        java.util.List<String> arguments = new java.util.ArrayList<>(java.util.List.of("-disableReviewPrompt",
                "-AppleLanguages", "(" + Config.language() + ")",
                "-AppleLocale", Config.locale()));
        arguments.addAll(java.util.List.of(extraArguments));
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("bundleId", Config.bundleId());
        params.put("arguments", arguments);
        driver.executeScript("mobile: launchApp", params);
    }
}
