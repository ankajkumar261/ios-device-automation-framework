package com.portfolio.ios.model;

/**
 * The daily calorie and macro targets the app derives from a profile — an
 * independent oracle for what Home should display.
 *
 * <p>Mirrors {@code UserProfile.deriveTargets} in the app
 * ({@code Models/UserProfile.swift:142-201}): Mifflin-St Jeor BMR × activity
 * multiplier, plus a daily delta from the weekly pace (or a goal default),
 * floored at 1200 kcal, then split into macros. The target is computed on the
 * device; no backend is involved, so it is deterministic for a given profile.
 *
 * <p>Rounding matches the app: calories are {@code Int(...)} (truncated), and
 * Home displays each macro target as {@code Int(target)} grams.
 */
public record ExpectedTargets(int calories, int proteinG, int carbsG, int fatG) {

    /**
     * @param profile        the onboarding profile
     * @param appliedGoal    the goal the app actually stored — not necessarily
     *                       {@code profile.goal()}: onboarding automation does not
     *                       select a goal yet, so the app keeps "Maintain Weight"
     * @param weeklyChangeKg signed weekly pace; the app forces 0 for goals other
     *                       than lose/gain
     * @param currentYear    the device's year; the app derives age as
     *                       {@code currentYear - birthYear}
     */
    public static ExpectedTargets of(Profile profile, OnboardingOptions.Goal appliedGoal,
                                     double weeklyChangeKg, int currentYear) {
        int age = currentYear - profile.birthYear();
        double bmr = 10 * profile.weightKg() + 6.25 * profile.heightCm() - 5 * age
                + sexOffset(profile.sex());
        double tdee = bmr * activityMultiplier(profile.activity());

        double dailyDelta;
        if (weeklyChangeKg != 0) {
            dailyDelta = weeklyChangeKg * 7700 / 7;
        } else if (appliedGoal == OnboardingOptions.Goal.LOSE_WEIGHT) {
            dailyDelta = -500;
        } else if (appliedGoal == OnboardingOptions.Goal.GAIN_WEIGHT) {
            dailyDelta = 300;
        } else {
            dailyDelta = 0;
        }

        int calories = Math.max(1200, (int) (tdee + dailyDelta));

        double protein;
        double carbs;
        double fat;
        if (appliedGoal == OnboardingOptions.Goal.MANAGE_BLOOD_SUGAR) {
            protein = 0.35;
            carbs = 0.30;
            fat = 0.35;
        } else if (appliedGoal == OnboardingOptions.Goal.LOSE_WEIGHT) {
            protein = 0.35;
            carbs = 0.35;
            fat = 0.30;
        } else {
            protein = 0.30;
            carbs = 0.40;
            fat = 0.30;
        }
        return new ExpectedTargets(calories,
                (int) (calories * protein / 4.0),
                (int) (calories * carbs / 4.0),
                (int) (calories * fat / 9.0));
    }

    /** {@code UserProfile.computeBMR}: +5 male, −161 female, −78 not specified. */
    private static double sexOffset(OnboardingOptions.Sex sex) {
        return switch (sex) {
            case MALE -> 5;
            case FEMALE -> -161;
            case PREFER_NOT_TO_SAY -> -78;
        };
    }

    /**
     * {@code ActivityLevel.multiplier}, via the onboarding rows that map onto
     * it ({@code OnboardingView.swift:2299-2311}): "Not active" → sedentary,
     * "Slightly active" → light, "Active" → moderate, "Highly active" → active.
     * Onboarding has no row for the app's extra-active level.
     */
    private static double activityMultiplier(OnboardingOptions.Activity activity) {
        return switch (activity) {
            case SEDENTARY -> 1.2;
            case LIGHTLY_ACTIVE -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE, EXTRA_ACTIVE -> 1.725;
        };
    }
}
