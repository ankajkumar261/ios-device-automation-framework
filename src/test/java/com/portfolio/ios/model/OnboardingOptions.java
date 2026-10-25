package com.portfolio.ios.model;

/**
 * Every selectable option in onboarding, with the <b>exact on-screen label</b>
 * the app renders.
 *
 * <p>Labels are transcribed from the app source rather than guessed:
 * option enums in {@code Models/UserProfile.swift} map each case to an
 * {@code AppStrings.Options} accessor, and each accessor's
 * {@code NSLocalizedString} value was read out of
 * {@code en.lproj/Localizable.strings}. Because locators match on visible text,
 * these constants are load-bearing — if the app's copy changes, they change here
 * and the tests still mean the same thing.
 */
public final class OnboardingOptions {

    private OnboardingOptions() {
    }

    /** Step 6 — {@code BiologicalSex}. */
    public enum Sex {
        MALE("Male"),
        FEMALE("Female"),
        PREFER_NOT_TO_SAY("Prefer not to say");

        private final String label;

        Sex(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 2 — {@code option.goal.*}. */
    public enum Goal {
        LOSE_WEIGHT("Lose Weight"),
        GAIN_WEIGHT("Gain Weight"),
        MAINTAIN_WEIGHT("Maintain Weight"),
        EAT_AND_LIVE_HEALTHIER("Eat & Live Healthier"),
        MANAGE_BLOOD_SUGAR("Manage Blood Sugar"),
        GET_MEAL_GUIDANCE("Get Meal Guidance");

        private final String label;

        Goal(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 8 — {@code DietType}. */
    public enum Diet {
        OMNIVORE("Omnivore"),
        VEGETARIAN("Vegetarian"),
        VEGAN("Vegan"),
        PESCATARIAN("Pescatarian"),
        KETO("Keto"),
        PALEO("Paleo"),
        CUSTOM("Custom");

        private final String label;

        Diet(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Step 21 — activity rows as currently rendered.
     *
     * <p>The labels are the cards' visible titles, not the underlying enum's
     * display names. The four-row redesign shows "Not active", "Slightly
     * active", "Active", and "Highly active"; "Very Active" and "Extra Active"
     * both resolve to the "Highly active" card.
     */
    public enum Activity {
        SEDENTARY("Not active"),
        LIGHTLY_ACTIVE("Slightly active"),
        MODERATELY_ACTIVE("Active"),
        VERY_ACTIVE("Highly active"),
        EXTRA_ACTIVE("Highly active");

        private final String label;

        Activity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 10 — {@code HealthConcernCatalog}. */
    public enum HealthConcern {
        DIABETES("Diabetes"),
        HIGH_BLOOD_PRESSURE("High blood pressure"),
        HIGH_CHOLESTEROL("High cholesterol"),
        HEART_DISEASE("Heart disease"),
        PCOS("PCOS"),
        THYROID("Thyroid"),
        IBS("IBS"),
        GERD("GERD"),
        KIDNEY_ISSUES("Kidney issues"),
        ANEMIA("Anemia");

        private final String label;

        HealthConcern(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 10 — {@code AllergyCatalog}. */
    public enum Allergy {
        PEANUTS("Peanuts"),
        TREE_NUTS("Tree nuts"),
        DAIRY("Dairy"),
        EGGS("Eggs"),
        SOY("Soy"),
        WHEAT_GLUTEN("Wheat / Gluten"),
        FISH("Fish"),
        SHELLFISH("Shellfish"),
        SESAME("Sesame");

        private final String label;

        Allergy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 11 — {@code option.commit.*}. */
    public enum Commitment {
        DRINK_MORE_WATER("Drink more water"),
        EAT_BALANCED_MEALS("Eat balanced meals"),
        EAT_LESS_SUGAR("Eat less sugar"),
        EXERCISE_REGULARLY("Exercise regularly"),
        TRACK_MY_CALORIES("Track my calories");

        private final String label;

        Commitment(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Step 12 — {@code option.struggle.*}. */
    public enum Struggle {
        OVEREATING("Overeating"),
        SKIPPING_MEALS("Skipping Meals"),
        LOW_PROTEIN_INTAKE("Low Protein Intake"),
        LATE_NIGHT_SNACKING("Late-Night Snacking"),
        SUGAR_CRAVINGS("Sugar Cravings"),
        MEAL_PLANNING("Meal Planning");

        private final String label;

        Struggle(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}