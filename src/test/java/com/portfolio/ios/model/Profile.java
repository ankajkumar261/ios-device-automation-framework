package com.portfolio.ios.model;

import java.util.List;

/**
 * The profile a test drives onboarding with.
 *
 * <p>Built via {@link #builder()} so a test can override only the field it is
 * actually asserting on. Defaults are a coherent mid-range adult — weight and
 * target weight differ so step 20's pace projection has a gap to work with, and
 * the BMI readout on step 18 lands in "Normal" rather than on a category
 * boundary.
 *
 * <p>Units are metric (cm / kg). The app exposes a KG/LB toggle on the weight
 * steps; the suite stays metric because the ruler's point-per-unit scale differs
 * between the two and metric is the unit the source's ranges are authored in.
 */
public final class Profile {

    private final OnboardingOptions.Sex sex;
    private final int birthYear;
    private final double heightCm;
    private final double weightKg;
    private final double goalWeightKg;
    private final OnboardingOptions.Goal goal;
    private final OnboardingOptions.Diet diet;
    /**
     * Step 9 eating window, in 24-hour clock time. The app's own hour list
     * excludes the opposite hour, so {@code startHour24 == endHour24} is not
     * reachable and the builder rejects it.
     */
    private final int eatingWindowStartHour24;
    private final int eatingWindowEndHour24;
    private final OnboardingOptions.Activity activity;
    private final List<OnboardingOptions.Allergy> allergies;
    private final List<OnboardingOptions.HealthConcern> healthConcerns;
    private final List<OnboardingOptions.Commitment> commitments;
    private final List<OnboardingOptions.Struggle> struggles;
    private final boolean connectAppleHealth;
    /** Step 24 hold. The app commits at 1.3s; the default leaves headroom. */
    private final long holdMillis;

    private Profile(Builder b) {
        this.sex = b.sex;
        this.birthYear = b.birthYear;
        this.heightCm = b.heightCm;
        this.weightKg = b.weightKg;
        this.goalWeightKg = b.goalWeightKg;
        this.goal = b.goal;
        this.diet = b.diet;
        this.eatingWindowStartHour24 = b.eatingWindowStartHour24;
        this.eatingWindowEndHour24 = b.eatingWindowEndHour24;
        this.activity = b.activity;
        this.allergies = List.copyOf(b.allergies);
        this.healthConcerns = List.copyOf(b.healthConcerns);
        this.commitments = List.copyOf(b.commitments);
        this.struggles = List.copyOf(b.struggles);
        this.connectAppleHealth = b.connectAppleHealth;
        this.holdMillis = b.holdMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public OnboardingOptions.Sex sex() {
        return sex;
    }

    public int birthYear() {
        return birthYear;
    }

    public double heightCm() {
        return heightCm;
    }

    public double weightKg() {
        return weightKg;
    }

    public double goalWeightKg() {
        return goalWeightKg;
    }

    public OnboardingOptions.Goal goal() {
        return goal;
    }

    public OnboardingOptions.Diet diet() {
        return diet;
    }

    public int eatingWindowStartHour24() {
        return eatingWindowStartHour24;
    }

    public int eatingWindowEndHour24() {
        return eatingWindowEndHour24;
    }

    public OnboardingOptions.Activity activity() {
        return activity;
    }

    public List<OnboardingOptions.Allergy> allergies() {
        return allergies;
    }

    public List<OnboardingOptions.HealthConcern> healthConcerns() {
        return healthConcerns;
    }

    public List<OnboardingOptions.Commitment> commitments() {
        return commitments;
    }

    public List<OnboardingOptions.Struggle> struggles() {
        return struggles;
    }

    public boolean connectAppleHealth() {
        return connectAppleHealth;
    }

    public long holdMillis() {
        return holdMillis;
    }

    @Override
    public String toString() {
        return "Profile{sex=" + sex.label()
                + ", birthYear=" + birthYear
                + ", height=" + heightCm + "cm"
                + ", weight=" + weightKg + "kg"
                + ", goalWeight=" + goalWeightKg + "kg"
                + ", goal=" + goal.label()
                + ", diet=" + diet.label()
                + ", activity=" + activity.label()
                + ", allergies=" + allergies
                + ", concerns=" + healthConcerns
                + ", commitments=" + commitments
                + ", struggles=" + struggles
                + ", appleHealth=" + connectAppleHealth
                + "}";
    }

    /** Mutable builder; only the fields a test cares about need overriding. */
    public static final class Builder {
        private OnboardingOptions.Sex sex = OnboardingOptions.Sex.FEMALE;
        private int birthYear = 1994;
        private double heightCm = 170;
        private double weightKg = 70;
        private double goalWeightKg = 65;
        private OnboardingOptions.Goal goal = OnboardingOptions.Goal.LOSE_WEIGHT;
        private OnboardingOptions.Diet diet = OnboardingOptions.Diet.OMNIVORE;
        private int eatingWindowStartHour24 = 8;
        private int eatingWindowEndHour24 = 20;
        private OnboardingOptions.Activity activity = OnboardingOptions.Activity.MODERATELY_ACTIVE;
        private List<OnboardingOptions.Allergy> allergies = List.of();
        private List<OnboardingOptions.HealthConcern> healthConcerns = List.of();
        private List<OnboardingOptions.Commitment> commitments =
                List.of(OnboardingOptions.Commitment.TRACK_MY_CALORIES);
        private List<OnboardingOptions.Struggle> struggles = List.of();
        private boolean connectAppleHealth = false;
        private long holdMillis = 2000;

        public Builder sex(OnboardingOptions.Sex value) {
            this.sex = value;
            return this;
        }

        public Builder birthYear(int value) {
            this.birthYear = value;
            return this;
        }

        public Builder heightCm(double value) {
            this.heightCm = value;
            return this;
        }

        public Builder weightKg(double value) {
            this.weightKg = value;
            return this;
        }

        public Builder goalWeightKg(double value) {
            this.goalWeightKg = value;
            return this;
        }

        public Builder goal(OnboardingOptions.Goal value) {
            this.goal = value;
            return this;
        }

        public Builder diet(OnboardingOptions.Diet value) {
            this.diet = value;
            return this;
        }

        /**
         * @param startHour24 window open, 0-23
         * @param endHour24   window close, 0-23; must differ from the start
         *                    because the app's hour list omits the opposite hour
         */
        public Builder eatingWindow(int startHour24, int endHour24) {
            requireHour(startHour24, "start");
            requireHour(endHour24, "end");
            if (startHour24 == endHour24) {
                throw new IllegalArgumentException(
                        "eatingWindow start and end cannot both be " + startHour24
                                + " — the app's hour list excludes the opposite hour");
            }
            this.eatingWindowStartHour24 = startHour24;
            this.eatingWindowEndHour24 = endHour24;
            return this;
        }

        private static void requireHour(int hour, String which) {
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException(
                        "eatingWindow " + which + "Hour24=" + hour + " is outside 0-23");
            }
        }

        public Builder activity(OnboardingOptions.Activity value) {
            this.activity = value;
            return this;
        }

        public Builder allergies(List<OnboardingOptions.Allergy> value) {
            this.allergies = value;
            return this;
        }

        public Builder healthConcerns(List<OnboardingOptions.HealthConcern> value) {
            this.healthConcerns = value;
            return this;
        }

        public Builder commitments(List<OnboardingOptions.Commitment> value) {
            this.commitments = value;
            return this;
        }

        public Builder struggles(List<OnboardingOptions.Struggle> value) {
            this.struggles = value;
            return this;
        }

        public Builder connectAppleHealth(boolean value) {
            this.connectAppleHealth = value;
            return this;
        }

        public Builder holdMillis(long value) {
            this.holdMillis = value;
            return this;
        }

        public Profile build() {
            return new Profile(this);
        }
    }
}