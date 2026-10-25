package com.portfolio.ios.tests.food;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.pages.FoodLogSheet;
import com.portfolio.ios.pages.FoodSearchPage;
import com.portfolio.ios.pages.HomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 2 — food search and manual logging ("+" → Search → log sheet).
 *
 * <p>Uses only the app's built-in foods, whose values are hard-coded:
 * Banana 105 kcal (C 27 g), Apple 95, Egg (Boiled) 78, Chicken Breast
 * (Grilled) 165 per 100 g, Greek Yogurt 100.
 *
 * <p>State: one onboarded app per class (see {@link AbstractAppTest}). Each
 * test logs a different food and asserts the <i>change</i> it causes, so tests
 * stay independent without a reset between them.
 *
 * <p>The log sheet rounds values while Home truncates them (raised with the
 * iOS team). Assertions use only values on which both agree: calories at whole
 * servings and exact grams.
 */
@Epic("Food")
@Feature("Search & log")
public class FoodLoggingTest extends AbstractAppTest {

    @Test(groups = {"food", "smoke", "regression", "p0"},
            description = "FOOD_001 Search and log a food")
    @Description("Banana logged as Breakfast lowers Remaining by 105, adds 27 g carbs, "
            + "and appears in the Breakfast card")
    public void food001_searchAndLogFood() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();
        int carbsBefore = home.macros().get("Carbs").eatenG();

        FoodSearchPage search = home.openFoodSearch().search("banana");
        assertTrue(search.isListed("Banana"), "Banana listed for 'banana'");
        FoodLogSheet sheet = search.open("Banana").selectMeal("Breakfast");
        assertEquals(sheet.kcal(), 105, "sheet calories for 1 banana");

        home = sheet.log();
        assertEquals(home.remainingKcal(), remainingBefore - 105, "Remaining after logging 105 kcal");
        assertEquals(home.macros().get("Carbs").eatenG(), carbsBefore + 27, "carbs after one banana");
        assertTrue(home.mealCardContains("BREAKFAST", "Banana"), "Banana in the BREAKFAST card");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_002 Add Food screen basics")
    @Description("Add Food shows its title, the five diet pills and built-in foods; "
            + "Cancel returns to Home without logging")
    public void food002_searchScreenBasics() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        FoodSearchPage search = home.openFoodSearch();
        for (String pill : FoodSearchPage.DIET_PILLS) {
            assertTrue(search.isDietPillShown(pill), "diet pill " + pill);
        }
        assertTrue(search.rowLabel("Egg (Boiled)").contains("78 kcal"), "built-in Egg (Boiled), 78 kcal");

        search.close();
        home.waitUntilLoaded();
        assertEquals(home.remainingKcal(), remainingBefore, "Cancel must not log anything");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_003 Servings stepper")
    @Description("+ doubles a banana to 210 kcal and 54 g carbs; − returns to 105 and stops at one serving")
    public void food003_servingsStepper() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        FoodLogSheet sheet = home.openFoodSearch().search("banana").open("Banana");
        assertEquals(sheet.quantityCaption(), "Servings", "a banana is counted in servings");
        assertEquals(sheet.quantity(), "1");

        sheet.increase();
        assertEquals(sheet.quantity(), "2");
        assertEquals(sheet.kcal(), 210, "2 bananas");
        assertEquals(sheet.macroGrams("C:"), 54, "carbs for 2 bananas");

        sheet.decrease();
        assertEquals(sheet.quantity(), "1");
        assertEquals(sheet.kcal(), 105, "back to 1 banana");

        sheet.decrease();
        assertEquals(sheet.quantity(), "1", "minus never goes below one serving");

        sheet.cancel().close();
        home.waitUntilLoaded();
        assertEquals(home.remainingKcal(), remainingBefore, "nothing logged");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_004 Gram-based amount")
    @Description("Chicken Breast (Grilled) is measured in grams: + adds 5 g, 105 g is 173 kcal, "
            + "and logging it lowers Remaining by 173")
    public void food004_gramBasedAmount() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        FoodLogSheet sheet = home.openFoodSearch().search("chicken breast").open("Chicken Breast (Grilled)");
        assertEquals(sheet.quantityCaption(), "Amount", "chicken is measured as an amount");
        assertEquals(sheet.quantity(), "100", "default 100 g");
        assertEquals(sheet.kcal(), 165, "165 kcal per 100 g");

        sheet.increase();
        assertEquals(sheet.quantity(), "105", "+ adds one 5 g step");
        assertEquals(sheet.kcal(), 173, "105 g × 1.65 kcal/g = 173.25");

        home = sheet.selectMeal("Lunch").log();
        assertEquals(home.remainingKcal(), remainingBefore - 173, "Remaining after 105 g of chicken");
        assertTrue(home.mealCardContains("LUNCH", "Chicken Breast (Grilled)"), "chicken in the LUNCH card");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_005 Meal type grouping")
    @Description("Apple logged as Snack and Egg (Boiled) as Lunch land in their own meal cards")
    public void food005_mealTypeGrouping() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        home = home.openFoodSearch().search("apple").open("Apple").selectMeal("Snack").log();
        home = home.openFoodSearch().search("egg").open("Egg (Boiled)").selectMeal("Lunch").log();

        assertEquals(home.remainingKcal(), remainingBefore - 95 - 78, "Remaining after apple + egg");
        assertTrue(home.mealCardContains("SNACK", "Apple"), "Apple in the SNACK card");
        assertTrue(home.mealCardContains("LUNCH", "Egg (Boiled)"), "Egg (Boiled) in the LUNCH card");
        assertFalse(home.mealCardContains("SNACK", "Egg (Boiled)"), "Egg (Boiled) not in SNACK");
    }

    @Test(groups = {"food", "regression", "p2"},
            description = "FOOD_006 Diet pill filter")
    @Description("Deselecting Non-Veg hides Chicken Breast (Grilled); selecting it again brings it back")
    public void food006_dietPillFilter() {
        FoodSearchPage search = home().openFoodSearch().search("chicken");
        assertTrue(search.isListed("Chicken Breast (Grilled)"), "chicken listed with all pills on");

        search.toggleDietPill("Non-Veg");
        assertTrue(Waits.untilQuietly(Waits.SHORT, () -> !search.isListed("Chicken Breast (Grilled)")),
                "Non-Veg off hides chicken");

        search.toggleDietPill("Non-Veg");
        assertTrue(Waits.untilQuietly(Waits.SHORT, () -> search.isListed("Chicken Breast (Grilled)")),
                "Non-Veg back on shows chicken again");
        search.close();
    }

    @Test(groups = {"food", "regression", "p2"},
            description = "FOOD_007 Built-in search results")
    @Description("'egg' finds exactly the three built-in egg dishes")
    public void food007_builtInSearchResults() {
        FoodSearchPage search = home().openFoodSearch().search("egg");
        assertEquals(search.localRowsContaining(", Egg ("), 3,
                "Egg (Boiled), Egg (Fried) and Egg (Scrambled)");
        search.close();
    }

    @Test(groups = {"food", "regression", "p3"},
            description = "FOOD_008 No results")
    @Description("A query that matches nothing shows the No Results state")
    public void food008_noResults() {
        FoodSearchPage search = home().openFoodSearch().search("zzqx");
        assertTrue(search.showsNoResults(), "'No Results' for zzqx");
        search.close();
    }

    @Test(groups = {"food", "regression", "p3"},
            description = "FOOD_009 Zero quantity cannot be logged")
    @Description("A quantity of 0 disables Log; cancelling leaves Remaining unchanged")
    public void food009_zeroQuantityCannotBeLogged() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        FoodLogSheet sheet = home.openFoodSearch().search("banana").open("Banana").setQuantity("0");
        assertFalse(sheet.isLogEnabled(), "Log must be disabled at quantity 0");

        sheet.cancel().close();
        home.waitUntilLoaded();
        assertEquals(home.remainingKcal(), remainingBefore, "nothing logged");
    }

    @Test(groups = {"food", "regression", "p2"},
            description = "FOOD_010 Logged food survives a relaunch")
    @Description("Greek Yogurt logged as Dinner is still listed, and Remaining unchanged, after relaunch")
    public void food010_loggedFoodSurvivesRelaunch() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();

        home = home.openFoodSearch().search("greek yogurt").open("Greek Yogurt").selectMeal("Dinner").log();
        int remainingAfterLog = home.remainingKcal();
        assertEquals(remainingAfterLog, remainingBefore - 100, "Remaining after 100 kcal of yogurt");

        driver.terminateApp(Config.bundleId());
        driver.activateApp(Config.bundleId());
        home.waitUntilLoaded();

        assertEquals(home.remainingKcal(), remainingAfterLog, "Remaining survives a relaunch");
        assertTrue(home.mealCardContains("DINNER", "Greek Yogurt"), "Greek Yogurt still in the DINNER card");
    }
}
