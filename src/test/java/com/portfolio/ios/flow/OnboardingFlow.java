package com.portfolio.ios.flow;

import com.portfolio.ios.core.Artifacts;
import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.model.Profile;
import com.portfolio.ios.pages.ActivityHealthPage;
import com.portfolio.ios.pages.BodyMetricsPage;
import com.portfolio.ios.pages.DietLifestylePage;
import com.portfolio.ios.pages.HoldGatePage;
import com.portfolio.ios.pages.InterstitialPages;
import com.portfolio.ios.pages.IntroCarouselPage;
import com.portfolio.ios.pages.PlanRevealPage;
import com.portfolio.ios.pages.ProfileBasicsPage;
import com.portfolio.ios.pages.StepPage;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the onboarding flow as a sequence of independently runnable phases.
 *
 * <p>The 27 steps are grouped into phases that mirror the app's own structure —
 * each phase ends on a screen the next phase can assert its starting point
 * from. That grouping is not cosmetic: it is what lets a test start at, say,
 * {@link Phase#BODY_METRICS} without replaying twenty minutes of earlier screens,
 * and it keeps a failure local to one part of the flow instead of pointing at
 * "onboarding" as a single 27-step monolith.
 *
 * <p>Phases are expressed as named steps so the Allure report shows the journey
 * as a readable path rather than one opaque test.
 */
public final class OnboardingFlow {

    private final IOSDriver driver;
    private final DriverFactory factory;
    private final Profile profile;

    public OnboardingFlow(IOSDriver driver, DriverFactory factory, Profile profile) {
        this.driver = driver;
        this.factory = factory;
        this.profile = profile;
    }

    /**
     * The unit of independent execution. Each phase knows how to place the app
     * on its first screen and how to walk off the end of it.
     */
    public enum Phase {
        INTRO(0, 5),
        PROFILE_BASICS(6, 7),
        DIET_LIFESTYLE(8, 13),
        INTERSTITIALS(14, 16),
        BODY_METRICS(17, 20),
        ACTIVITY_HEALTH(21, 22),
        CLOSING(23, 26);

        private final int firstStep;
        private final int lastStep;

        Phase(int firstStep, int lastStep) {
            this.firstStep = firstStep;
            this.lastStep = lastStep;
        }

        public int firstStep() {
            return firstStep;
        }

        public int lastStep() {
            return lastStep;
        }

        /** The phase that must run immediately before this one. */
        public Phase previous() {
            Phase[] all = values();
            return ordinal() == 0 ? null : all[ordinal() - 1];
        }
    }

    // ---------------------------------------------------------------- phases

    /** Steps 0-5: the six full-bleed video intros. */
    @Step("Phase 1 — carousel (steps 0-5)")
    public void runIntro() {
        for (OnboardingStep step : CAROUSEL) {
            advanceOnePanel(step);
        }
    }

    /**
     * Advances exactly one carousel panel.
     *
     * <p>Separate from {@link #runIntro()} because "is each screen correct" and
     * "does the whole carousel work" are different questions, and only the
     * second one can be answered by tapping Continue six times.
     *
     * @param current the panel currently on screen
     */
    @Step("Advance carousel panel {current}")
    public void advanceOnePanel(OnboardingStep current) {
        new IntroCarouselPage(driver, factory, current).advanceIntro(nextCarouselStep(current));
    }

    /**
     * The carousel screens in order, paired with the screen each one leads to.
     *
     * <p>Step 5 is the exception: it is the last video panel and leads to
     * {@link OnboardingStep#GENDER}, not to another carousel screen.
     */
    private static final OnboardingStep[] CAROUSEL = {
            OnboardingStep.WELCOME,
            OnboardingStep.ACHIEVE_GOALS,
            OnboardingStep.GOAL,
            OnboardingStep.ACHIEVE_MORE,
            OnboardingStep.REACH_GOALS,
            OnboardingStep.FIND_COACH,
    };

    /**
     * The six carousel screens, in order.
     *
     * <p>Public so a test can assert each one individually instead of only
     * checking the phase as a whole.
     */
    public static List<OnboardingStep> carouselSteps() {
        return List.of(CAROUSEL);
    }

    private static OnboardingStep nextCarouselStep(OnboardingStep current) {
        for (int i = 0; i < CAROUSEL.length; i++) {
            if (CAROUSEL[i] == current) {
                return i == CAROUSEL.length - 1 ? OnboardingStep.GENDER : CAROUSEL[i + 1];
            }
        }
        throw new IllegalArgumentException(current + " is not part of the intro carousel");
    }

    /** Steps 6-7: sex and birth year. */
    @Step("Phase 2 — profile basics (steps 6-7)")
    public void runProfileBasics() {
        ProfileBasicsPage page = new ProfileBasicsPage(driver, factory);
        page.selectSex(profile.sex());
        page.setBirthYear(profile.birthYear());
    }

    /** Steps 8-13: diet through the closing benefit card. */
    @Step("Phase 3 — diet and lifestyle (steps 8-13)")
    public void runDietLifestyle() {
        DietLifestylePage page = new DietLifestylePage(driver, factory);
        page.selectDiet(profile.diet());
        page.setEatingWindow(profile.eatingWindowStartHour24(), profile.eatingWindowEndHour24());
        page.selectHealthConcerns(profile.healthConcerns());
        page.selectCommitments(profile.commitments());
        page.selectStruggles(profile.struggles());
        page.advancePastBenefits();
    }

    /** Steps 14-16: loader, coach video, intro card. */
    @Step("Phase 4 — interstitials (steps 14-16)")
    public void runInterstitials() {
        InterstitialPages page = new InterstitialPages(driver, factory);
        page.waitOutAlmostThere();
        page.advancePastCoachVideo();
        page.advancePastLetsStart();
    }

    /** Steps 17-20: the three rulers and the pace slider. */
    @Step("Phase 5 — body metrics (steps 17-20)")
    public void runBodyMetrics() {
        BodyMetricsPage page = new BodyMetricsPage(driver, factory);
        page.setHeightCm(profile.heightCm());
        page.setWeightKg(profile.weightKg());
        page.setGoalWeightKg(profile.goalWeightKg());
        page.setPace(0.5);
    }

    /** Steps 21-22: activity level and the Apple Health offer. */
    @Step("Phase 6 — activity and Apple Health (steps 21-22)")
    public void runActivityHealth() {
        ActivityHealthPage page = new ActivityHealthPage(driver, factory);
        page.selectActivity(profile.activity());
        page.completeAppleHealth(profile.connectAppleHealth());
    }

    /**
     * Steps 23-26: thanks video, hold gate, AI plan generation, final CTA.
     *
     * <p>The only phase that talks to the backend, and therefore the only one
     * worth excluding from an inner dev loop.
     */
    @Step("Phase 7 — closing (steps 23-26)")
    public boolean runClosing() {
        InterstitialPages interstitials = new InterstitialPages(driver, factory);
        interstitials.advancePastThanks();

        new HoldGatePage(driver, factory).holdToUnlock(profile.holdMillis());

        PlanRevealPage plan = new PlanRevealPage(driver, factory);
        plan.waitForPlanGeneration();
        return plan.finishOnboarding();
    }

    // ---------------------------------------------------------------- whole flow

    /** Walks all 27 steps in order. */
    @Step("Complete the full onboarding flow")
    public boolean runAll() {
        runIntro();
        runProfileBasics();
        runDietLifestyle();
        runInterstitials();
        runBodyMetrics();
        runActivityHealth();
        return runClosing();
    }

    // ---------------------------------------------------------------- verification

    /**
     * Asserts the app is showing {@code expected}.
     *
     * <p>Used by each phase test to prove the previous phase left the app where
     * the next one expects to start. On mismatch the accessibility page source is
     * captured, since with no accessibility identifiers in this app the tree is
     * the only way to see what was actually on screen.
     */
    public void expectStep(OnboardingStep expected) {
        StepPage probe = new StepPage(driver, factory, expected) { };
        try {
            probe.assertCurrent();
        } catch (RuntimeException e) {
            Artifacts.capture(driver, "expected-" + expected.name());
            throw e;
        }
    }

    /**
     * Asserts the app is showing {@code expected}, or a later step.
     *
     * <p>For the self-advancing loader at step 14, which is only on screen for
     * about six seconds — asserting on it directly fails even when the flow is
     * correct, because the app has usually already moved on.
     */
    public void expectStepOrBeyond(OnboardingStep expected) {
        StepPage probe = new StepPage(driver, factory, expected) { };
        try {
            probe.waitForStepOrBeyond(expected, Waits.MEDIUM);
        } catch (RuntimeException e) {
            Artifacts.capture(driver, "expected-at-least-" + expected.name());
            throw e;
        }
    }

    /**
     * Screenshots the current screen into {@code Config.artifactsDir()}.
     *
     * <p>Useful when a failure says only "step 20 did not appear": a picture of
     * the screen at that moment is usually enough to tell a selector problem
     * from a flow problem.
     */
    @Step("Capture current screen")
    public void captureCurrentScreen(String label) {
        Artifacts.capture(driver, label);
    }

    /** Human-readable phase list, for test names and reports. */
    public static List<String> allPhaseNames() {
        List<String> names = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            names.add(phase.name() + " (steps " + phase.firstStep() + "-" + phase.lastStep() + ")");
        }
        return names;
    }
}