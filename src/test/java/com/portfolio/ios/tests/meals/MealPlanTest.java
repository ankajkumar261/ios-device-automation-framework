package com.portfolio.ios.tests.meals;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.Locators;
import com.portfolio.ios.pages.MealPlansPage;
import com.portfolio.ios.pages.MyPlanPage;
import com.portfolio.ios.pages.PlanMealDetailPage;
import com.portfolio.ios.pages.PlanPreviewPage;
import com.portfolio.ios.pages.PlanPreviewPage.PreviewMeal;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Module 8 — Meal plans: the six bundled plans and their previews, and,
 * in the {@code ai} group, building a plan on the production AI backend and
 * using it (log a meal, servings, future days, replacing, cancelling).
 *
 * <p>The list and previews come from the app's bundled
 * {@code diet_catalog.json}, so they are checked exactly ({@link #CATALOG}).
 * A built plan's meals are AI-written, so only their shape is checked.
 *
 * <p>The {@code ai} tests share one generated plan: MEAL_005 builds it,
 * the next ones use it, and MEAL_004 (cancel) runs last because a fast
 * backend can finish before Cancel lands. They are excluded from the
 * default suite; run them with {@code -Dgroups=ai}.
 */
@Epic("Meal plans")
@Feature("Diet plans")
public class MealPlanTest extends AbstractAppTest {

    /** A plan as the catalog defines it: name, calories per day, today's preview meals. */
    private record Plan(String name, String kcalRange, List<PreviewMeal> today) {
    }

    private static PreviewMeal meal(String slot, String name, int kcal) {
        return new PreviewMeal(slot, name, kcal);
    }

    /** From {@code Resources/diet_catalog.json}: the first dish of each slot is today's. */
    private static final List<Plan> CATALOG = List.of(
            new Plan("Mediterranean Wellness", "1700–2200 kcal/day", List.of(
                    meal("Breakfast", "Greek Yogurt with Berries", 240),
                    meal("Lunch", "Grilled Salmon Bowl", 480),
                    meal("Snack", "Hummus & Pita Plate", 210),
                    meal("Dinner", "Chicken & Olive Couscous", 520))),
            new Plan("Healthy Weight Loss", "1400–1700 kcal/day", List.of(
                    meal("Breakfast", "Veggie Omelette", 280),
                    meal("Lunch", "Chicken & Greens Bowl", 420),
                    meal("Snack", "Cottage Cheese & Apple", 180),
                    meal("Dinner", "Lemon Pepper Cod", 440))),
            new Plan("High-Protein Power", "1800–2400 kcal/day", List.of(
                    meal("Breakfast", "Egg White Omelette", 320),
                    meal("Lunch", "Chicken Caesar Bowl", 540),
                    meal("Snack", "Greek Yogurt + Almonds", 220),
                    meal("Dinner", "Steak & Sweet Potato", 620))),
            new Plan("Smart Carb Cycling", "1500–1900 kcal/day", List.of(
                    meal("Breakfast", "Banana Oat Pancakes", 360),
                    meal("Lunch", "Tuna Avocado Wrap", 410),
                    meal("Snack", "Mixed Nuts (1 oz)", 170),
                    meal("Dinner", "Grilled Chicken & Veggies", 480))),
            new Plan("Easy Low-Carb", "1500–1800 kcal/day", List.of(
                    meal("Breakfast", "Avocado Egg Toast", 340),
                    meal("Lunch", "Cobb Salad", 460),
                    meal("Snack", "Cheese & Cucumber", 190),
                    meal("Dinner", "Zucchini Noodle Stir-Fry", 430))),
            new Plan("Whole-Food Eating", "1700–2100 kcal/day", List.of(
                    meal("Breakfast", "Oats with Banana & Walnuts", 320),
                    meal("Lunch", "Brown Rice Veggie Bowl", 470),
                    meal("Snack", "Apple & Almond Butter", 200),
                    meal("Dinner", "Baked Chicken & Quinoa", 510))));

    private static final String GENERATED = "Mediterranean Wellness";

    private MealPlansPage meals() {
        return new MealPlansPage(driver, factory);
    }

    private MyPlanPage myPlan() {
        return new MyPlanPage(driver, factory);
    }

    /** My Plan, building the Mediterranean plan first if none exists yet. */
    private MyPlanPage ensurePlan() {
        meals().openTab();
        if (myPlan().isShowing()) {
            return myPlan();
        }
        meals().tapGenerate(GENERATED);
        return myPlan().waitUntilBuilt(Waits.AI_BACKEND);
    }

    /** Name of the active plan, from My Plan's list ("Current plan"). */
    private String activePlanName() {
        List<String> names = meals().planNames();
        List<String> actions = meals().planActions();
        return names.get(actions.indexOf("Current plan"));
    }

    // ---------------------------------------------------------------- catalog (no backend)

    @Test(groups = {"meals", "smoke", "regression", "p0"},
            description = "MEAL_001 Meals tab lists the six plans")
    @Description("With no plan the Meals tab shows 'Meal Plan', 'Available Meal Plans' and the six plans in "
            + "catalog order, each with '7 days', 'Plan Preview' and 'Generate'")
    public void meal001_listsSixPlans() {
        MealPlansPage list = meals().openTab();
        assertTrue(list.isPickerOnly(), "no plan yet: the tab is the plan list");
        assertEquals(list.planNames(), CATALOG.stream().map(Plan::name).toList(), "plans in order");
        assertEquals(list.planActions(), List.of("Generate", "Generate", "Generate", "Generate", "Generate",
                "Generate"), "every plan offers Generate");
        assertEquals(list.durationChips(), 6, "'7 days' on every card");
    }

    @Test(groups = {"meals", "regression", "p1"},
            description = "MEAL_002 Plan preview")
    @Description("Mediterranean preview: name, '1700–2200 kcal/day', '7-day plan', See more ↔ See less, Today + "
            + "Day 2–7 (Day 2 does not change the meals, by design), today's four catalog meals, the three "
            + "audience sections, 'Generate Plan'; Back returns to the list")
    public void meal002_planPreview() {
        PlanPreviewPage preview = meals().openTab().openPreview("Mediterranean Wellness");
        Plan plan = CATALOG.get(0);

        assertEquals(preview.name(), plan.name());
        assertEquals(preview.kcalRange(), plan.kcalRange());
        assertTrue(preview.showsText("7-day plan"), "'7-day plan' chip");
        assertEquals(preview.toggleDescription(), "See less");
        assertEquals(preview.toggleDescription(), "See more");

        assertEquals(preview.dayButtons(), List.of("Today", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7"));
        assertEquals(preview.todaysMeals(), plan.today(), "today's meals");
        preview.tapDay("Day 2");
        assertEquals(preview.todaysMeals(), plan.today(), "a future day does not change the preview");

        assertEquals(preview.sectionLines("Choose this plan if…", "Recommended for"), List.of(
                "You want sustainable, long-term healthy eating.",
                "You enjoy fish, olive oil, and fresh produce.",
                "You prefer balance over strict restriction.",
                "You're aiming for heart health and stable energy."));
        assertEquals(preview.sectionLines("Recommended for", "Not recommended for"), List.of(
                "Adults at any fitness level",
                "People with heart-health goals",
                "Those who dislike calorie counting",
                "Pregnant or breastfeeding women (with doctor's nod)"));
        assertEquals(preview.sectionLines("Not recommended for", "Sources & References"), List.of(
                "Strict carnivore or all-meat diets",
                "Severe seafood allergies",
                "Anyone needing rapid short-term weight loss"));
        assertTrue(preview.showsGeneratePlan(), "'Generate Plan'");

        assertTrue(preview.back().isPickerOnly(), "Back returns to the plan list");
    }

    @Test(groups = {"meals", "regression", "p2"},
            description = "MEAL_003 Every plan's preview matches the catalog")
    @Description("For each of the six plans the preview shows its calorie range and today's four catalog meals")
    public void meal003_everyPreviewMatchesCatalog() {
        MealPlansPage list = meals().openTab();
        for (Plan plan : CATALOG) {
            PlanPreviewPage preview = list.openPreview(plan.name());
            assertEquals(preview.name(), plan.name());
            assertEquals(preview.kcalRange(), plan.kcalRange(), plan.name() + " calories");
            assertEquals(preview.todaysMeals(), plan.today(), plan.name() + " meals");
            list = preview.back();
        }
    }

    // ---------------------------------------------------------------- built plan (AI backend)

    @Test(groups = {"meals", "ai", "regression", "p1"}, priority = 1,
            description = "MEAL_005 Generate a plan")
    @Description("Generate on Mediterranean shows 'Building your Mediterranean Wellness', then My Plan: the plan's "
            + "name, Today and Day 2…, the four slot tabs, a meal card 'Name, N kcal, 1 serving', 0 eaten of the "
            + "daily goal, Reset Plan, and 'Current plan' on Mediterranean")
    public void meal005_generatePlan() {
        int goal = home().goalKcal();
        MealPlansPage list = meals().openTab();
        if (!list.isPickerOnly()) {
            throw new SkipException("A plan already exists in this run; MEAL_005 needs the plan list");
        }
        list.tapGenerate(GENERATED);
        Waits.until(Waits.MEDIUM, "the building cover or My Plan", () ->
                Waits.isPresent(driver, Locators.planGenerationTitle()) || myPlan().isShowing() ? Boolean.TRUE : null);
        if (Waits.isPresent(driver, Locators.planGenerationTitle())) {
            assertEquals(driver.findElement(Locators.planGenerationTitle()).getAttribute("label"),
                    "Building your " + GENERATED);
        } else {
            Allure.addAttachment("note", "The backend built the plan before its cover could be read.");
        }

        MyPlanPage plan = myPlan().waitUntilBuilt(Waits.AI_BACKEND);
        assertTrue(plan.showsText(GENERATED), "plan name on My Plan");
        List<String> days = plan.dayButtons();
        assertEquals(days.get(0), "Today");
        assertTrue(days.contains("Day, 2"), "day buttons: " + days);
        for (String slot : List.of("Breakfast", "Lunch", "Snack", "Dinner")) {
            assertTrue(plan.hasSlotTab(slot), "slot tab " + slot);
        }
        assertTrue(plan.mealCardLabel().matches(".+, [0-9,]+ kcal, 1 serving"), plan.mealCardLabel());
        assertEquals(plan.dayTotal()[0], 0, "nothing eaten from the plan yet");
        assertEquals(plan.dayTotal()[1], goal, "the day's target is the daily goal");
        assertEquals(activePlanName(), GENERATED, "'Current plan' on the built plan");
    }

    @Test(groups = {"meals", "ai", "regression", "p1"}, priority = 2,
            description = "MEAL_006 Log a plan meal")
    @Description("Today's breakfast: detail with SERVINGS, Ingredients, Directions, Allergens and 'Log this meal, "
            + "N kcal'; logging marks it Logged, counts it on My Plan, lists it under BREAKFAST on Home and lowers "
            + "Remaining by N")
    public void meal006_logPlanMeal() {
        HomePage home = home();
        home.scrollToTop();
        int remainingBefore = home.remainingKcal();

        MyPlanPage plan = ensurePlan().selectDay("Today").selectSlot("Breakfast");
        String name = plan.mealName();
        int kcal = plan.mealKcal();
        PlanMealDetailPage detail = plan.openMeal();
        assertEquals(detail.slot(), "BREAKFAST");
        assertEquals(detail.name(), name);
        assertEquals(detail.servings(), 1);
        for (String section : List.of("Ingredients", "Directions", "Allergens")) {
            assertTrue(detail.hasSection(section), "section " + section);
        }
        assertEquals(detail.logButtonLabel(), "Log this meal, " + kcal + " kcal");
        assertTrue(detail.isLogEnabled(), "today's meal can be logged");

        plan = detail.log();
        assertTrue(plan.showsLogged(), "'Logged' on the meal");
        assertEquals(plan.dayTotal()[0], kcal, "counted on My Plan");

        home = home().open();
        assertTrue(home.mealCardContains("BREAKFAST", name), name + " under BREAKFAST on Home");
        home.scrollToTop();
        int drop = remainingBefore - home.remainingKcal();
        // Home truncates and the plan rounds (rounding gap still open), so allow 1 kcal.
        assertTrue(Math.abs(drop - kcal) <= 1, "Remaining drops by " + kcal + ", dropped " + drop);
    }

    @Test(groups = {"meals", "ai", "regression", "p2"}, priority = 3,
            description = "MEAL_007 A future day's meal cannot be logged")
    @Description("On Day 2 the meal detail's button reads 'Future day — can't log yet' and is disabled")
    public void meal007_futureMealCannotBeLogged() {
        PlanMealDetailPage detail = ensurePlan().selectDay("Day, 2").openMeal();
        assertTrue(detail.logButtonLabel().startsWith("Future day — can't log yet"), detail.logButtonLabel());
        assertFalse(detail.isLogEnabled(), "disabled on a future day");
        detail.back();
    }

    @Test(groups = {"meals", "ai", "regression", "p2"}, priority = 4,
            description = "MEAL_009 Servings scale the calories")
    @Description("On today's lunch, + makes 2 servings at twice the calories and − returns to 1; − is off at 1")
    public void meal009_servingsScaleCalories() {
        PlanMealDetailPage detail = ensurePlan().selectDay("Today").selectSlot("Lunch").openMeal();
        assertEquals(detail.servings(), 1);
        assertFalse(detail.isStepEnabled("minus"), "− off at 1 serving");
        int single = detail.logKcal();

        detail.step("plus");
        assertEquals(detail.servings(), 2);
        assertTrue(Math.abs(detail.logKcal() - 2 * single) <= 1,
                "2 servings ≈ 2 × " + single + ", shows " + detail.logKcal());
        detail.step("minus");
        assertEquals(detail.logKcal(), single, "back to one serving");
        detail.back();
    }

    @Test(groups = {"meals", "ai", "regression", "p2"}, priority = 5,
            description = "MEAL_008 Replacing the plan asks first")
    @Description("With a plan active, Generate on another plan asks 'Replace your plan?'; 'Not now' keeps the "
            + "current plan")
    public void meal008_replaceAsksFirst() {
        ensurePlan();
        String active = activePlanName();
        String other = CATALOG.stream().map(Plan::name).filter(n -> !n.equals(active)).findFirst().orElseThrow();

        MealPlansPage list = meals();
        list.tapGenerate(other);
        assertTrue(list.showsReplaceDialog(), "'Replace your plan?'");
        assertEquals(list.replaceDialogMessage(),
                "This builds " + other + " and replaces the plan you're on now.");
        list.answerReplaceDialog("Not now");

        assertTrue(myPlan().isShowing(), "still on My Plan");
        assertEquals(activePlanName(), active, "the current plan is kept");
    }

    @Test(groups = {"meals", "ai", "regression", "p2"}, priority = 6,
            description = "MEAL_004 Cancel a plan while it builds")
    @Description("Replacing the plan and tapping Cancel on 'Building your …' keeps the current plan. Skipped when "
            + "the backend finishes before Cancel can be tapped")
    public void meal004_cancelWhileBuilding() {
        ensurePlan();
        String active = activePlanName();
        String other = CATALOG.stream().map(Plan::name).filter(n -> !n.equals(active)).findFirst().orElseThrow();

        MealPlansPage list = meals();
        list.tapGenerate(other);
        assertTrue(list.showsReplaceDialog(), "'Replace your plan?'");
        list.answerReplaceDialog("Generate");
        if (!Waits.isVisible(driver, Locators.visibleButton("Cancel"), Waits.SHORT)) {
            myPlan().waitUntilBuilt(Waits.AI_BACKEND);
            throw new SkipException("The backend built " + other + " before Cancel could be tapped");
        }
        driver.findElement(Locators.visibleButton("Cancel")).click();
        Waits.untilTrue(Waits.MEDIUM, "the building cover to close",
                () -> !Waits.isPresent(driver, Locators.planGenerationTitle()));

        assertTrue(myPlan().waitUntilLoaded().isShowing(), "back on My Plan");
        assertEquals(activePlanName(), active, "cancelling keeps the current plan");
        assertNotEquals(activePlanName(), other);
    }
}
