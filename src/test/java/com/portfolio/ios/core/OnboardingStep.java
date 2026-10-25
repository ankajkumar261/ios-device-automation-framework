package com.portfolio.ios.core;

import org.openqa.selenium.By;

/**
 * The 27 onboarding steps, mirrored from
 * {@code OnboardingView.stepName(for:)} in the app source.
 *
 * <p><b>Why this enum exists.</b> The app renders its progress indicator as a
 * plain {@code Capsule} inside a {@code GeometryReader} with no accessibility
 * modifier, so the step index is <i>not</i> observable from the accessibility
 * tree. Each constant instead carries a {@link #marker} — a distinctive slice of
 * that step's on-screen copy — so the framework can infer position from
 * content and make {@link #isCurrent} phealthcareble.
 *
 * <p>Markers come from {@code en.lproj/Localizable.strings} and the
 * {@code stepContent(for:)} router. Matching uses {@code CONTAINS} on purpose:
 * several headlines are assembled by concatenating localisation fragments
 * ("Almost " + "there..."), so the rendered label is a runtime join the app
 * source never spells out in full.
 *
 * <p>Sessions force {@code en} / {@code en_US} (see {@link Config#language()}),
 * because these markers are English copy. Switching the simulator to another
 * language invalidates every marker here — that is the debt to repay first if
 * this suite ever needs to run localised.
 *
 * <p><b>Markers must be unique to one screen.</b> They are matched with
 * {@code CONTAINS}, so a marker that is a substring of another screen's heading
 * matches both and makes a wait pass while the app is still on the wrong step.
 * That is not hypothetical: the diet heading is "Do You Have Any Dietary
 * Preferences?" and the health-concerns heading is "Do You Have Any Health
 * Concerns?", so a marker of "Do You Have Any" would happily match either.
 * Each marker is therefore taken from the part of its heading that is actually
 * distinctive.
 */
public enum OnboardingStep {

    // -- intro (no progress bar; topBar hidden for steps 0 and 1) --------
    WELCOME(0, "welcome", "AI-Powered"),
    ACHIEVE_GOALS(1, "achieve_goals", "Achieve Your Fitness"),
    GOAL(2, "goal", "What is Your Goal"),
    ACHIEVE_MORE(3, "achieve_more", "Helping You Achieve More"),
    REACH_GOALS(4, "reach_goals", "Stay Accountable and"),
    FIND_COACH(5, "find_coach", "Find the"),

    // -- data collection -------------------------------------------------
    GENDER(6, "gender", "What is Your Gender"),
    YEAR_OF_BIRTH(7, "year_of_birth", "What Is Your Birth Year"),
    DIET(8, "diet", "Do You Have Any Dietary"),
    EATING_WINDOW(9, "eating_window", "When do you usually eat"),
    HEALTH_CONCERNS(10, "health_concerns", "Health Concerns?"),
    COMMITMENTS(11, "commitments", "What Would You Like"),
    STRUGGLES(12, "struggles", "What do you struggle with"),
    BETTER_FOOD_CHOICES(13, "better_food_choices", "Support Better Food Choices"),
    ALMOST_THERE(14, "almost_there", "Almost There!"),
    MEET_COACH(15, "meet_coach", "Let's get started", "XCUIElementTypeButton"),
    LETS_START(16, "lets_start", "Answer a few"),
    HEIGHT(17, "height", "What is your height"),
    WEIGHT(18, "weight", "What is your weight"),
    GOAL_WEIGHT(19, "goal_weight", "What is your desired"),
    WEIGHT_SPEED(20, "weight_speed", "How fast do you want to"),
    ACTIVITY(21, "activity", "Tell us about your"),
    APPLE_HEALTH(22, "apple_health", "Connect Apple Health"),
    THANKS(23, "thanks", "I'm Ready!", "XCUIElementTypeButton"),
    TAP_AND_HOLD(24, "tap_and_hold", "Cal Care will help me"),
    CREATING_PLAN(25, "creating_plan", "Creating Your Plan"),
    PLAN_REVEAL(26, "plan_reveal", "Your Plan is Ready");

    /** Total step count, asserted against {@code OnboardingView.totalSteps}. */
    public static final int TOTAL = 27;

    private final int index;
    private final String analyticsName;
    private final String marker;
    /**
     * Element type the marker must be, or {@code null} to accept any type.
     *
     * <p>Needed where the same words appear as both a button label and part of a
     * nearby heading. Step 15's CTA is a button reading "Let's get started",
     * and step 16's heading opens "Let's get started! Answer a few quick
     * questions…" — so a label-only match for step 15 would also succeed on
     * step 16, and the flow would tap a Continue it had already passed.
     */
    private final String markerType;

    OnboardingStep(int index, String analyticsName, String marker) {
        this(index, analyticsName, marker, null);
    }

    OnboardingStep(int index, String analyticsName, String marker, String markerType) {
        this.index = index;
        this.analyticsName = analyticsName;
        this.marker = marker;
        this.markerType = markerType;
    }

    /** 0-based step index, matching the app's {@code currentStep}. */
    public int index() {
        return index;
    }

    /** Name the app sends to Firebase for this step. */
    public String analyticsName() {
        return analyticsName;
    }

    /** Distinctive English copy expected on screen while this step is current. */
    public String marker() {
        return marker;
    }

    /**
     * True when this step's marker is on screen. Used both as an assertion
     * ("we are on GENDER") and as a navigation guard before tapping, so a
     * mis-timed advance surfaces as a clear step mismatch rather than a
     * stale-element error three screens later.
     *
     * <p>This form waits. Do not call it from inside another polling loop; use
     * {@link #isCurrentNow} there.
     */
    public boolean isCurrent(io.appium.java_client.ios.IOSDriver driver) {
        return Waits.isVisible(driver, markerLocator(), Waits.SHORT);
    }

    /**
     * Immediate version of {@link #isCurrent}.
     *
     * <p>Polling loops must use this form. A waiting check nested inside another
     * wait turns every negative evaluation into a full timeout before the outer
     * loop can advance.
     */
    public boolean isCurrentNow(io.appium.java_client.ios.IOSDriver driver) {
        return Waits.isPresent(driver, markerLocator());
    }

    /** Predicate matching any element whose label contains this step's marker. */
    public By markerLocator() {
        if (markerType == null) {
            return com.portfolio.ios.pages.Locators.byLabelContaining(marker);
        }
        return com.portfolio.ios.pages.Locators.byTypeAndLabel(markerType, marker);
    }

    /**
     * True when this step — or any later one — is the screen currently shown.
     *
     * <p>Exists for the steps that advance on their own. {@link #ALMOST_THERE}
     * runs a fixed ~6s sequence and then moves to step 15 by itself, so a wait
     * that requires catching it exactly is a coin flip: poll slightly too late
     * and it is already gone, and the test fails while the app is exactly where
     * it should be. Accepting any later step removes the race without weakening
     * the check that matters — the app really did move forward.
     */
    public boolean isAtOrBeyond(io.appium.java_client.ios.IOSDriver driver) {
        OnboardingStep[] steps = values();
        // Check the expected step and later steps first. When waiting for a
        // transient screen, the app is normally either there or already past
        // it; checking earlier markers first would waste full negative lookups
        // on screens that have already gone.
        for (int i = index; i < steps.length; i++) {
            if (steps[i].isCurrentNow(driver)) {
                return true;
            }
        }
        for (int i = 0; i < index; i++) {
            if (steps[i].isCurrentNow(driver)) {
                return false;
            }
        }
        return false;
    }

    public static OnboardingStep ofIndex(int index) {
        for (OnboardingStep s : values()) {
            if (s.index == index) {
                return s;
            }
        }
        throw new IllegalArgumentException("No onboarding step at index " + index);
    }

    /**
     * Best-effort reverse lookup from an on-screen label, for diagnostics when a
     * failure says "unexpected screen". Returns {@code null} when nothing matches
     * rather than guessing — ambiguity is reported, not papered over.
     */
    public static OnboardingStep fromVisibleText(String label) {
        if (label == null) {
            return null;
        }
        String haystack = label.toLowerCase();
        OnboardingStep best = null;
        int bestLength = -1;
        for (OnboardingStep s : values()) {
            String needle = s.marker.toLowerCase();
            if (haystack.contains(needle) && needle.length() > bestLength) {
                best = s;
                bestLength = needle.length();
            }
        }
        return best;
    }
}