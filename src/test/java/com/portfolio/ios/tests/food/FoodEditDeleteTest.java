package com.portfolio.ios.tests.food;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.pages.FoodDetailPage;
import com.portfolio.ios.pages.FoodLogSheet;
import com.portfolio.ios.pages.HomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 3 — viewing, editing and deleting a logged food (tap a timeline row
 * on Home → detail → Edit / Trash).
 *
 * <p>Each test first logs its own built-in food through search (Module 2's
 * flow), then works on that entry and asserts the change it causes, so tests
 * stay independent without a reset. Whole servings only: the edit sheet rounds
 * while Home truncates (open question with the iOS team).
 *
 * <p>Foods: Whole Milk 150 kcal / 1 cup; Banana 105 / 1 medium; Apple 95;
 * Egg (Boiled) 78; Orange Juice 110 / 1 glass; Rice (White, Cooked) 206 /
 * 1 cup; Almonds 164 / 28 g; Peanut Butter 190 / 2 tbsp.
 */
@Epic("Food")
@Feature("Edit & delete")
public class FoodEditDeleteTest extends AbstractAppTest {

    /** Each test logs its own food onto a freshly onboarded day. */
    @Override
    protected boolean restoreBaselineBeforeEachTest() {
        return true;
    }

    /** Logs {@code food} as {@code meal} at its default quantity; returns Home. */
    private HomePage log(String query, String food, String meal) {
        return home().openFoodSearch().search(query).open(food).selectMeal(meal).log();
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_011 Logged food detail")
    @Description("Tapping a logged food opens its detail with name, meal, portion, calories and macros; "
            + "Back returns to Home")
    public void food011_loggedFoodDetail() {
        HomePage home = log("whole milk", "Whole Milk", "Breakfast");

        FoodDetailPage detail = home.openLoggedFood("Whole Milk");
        assertEquals(detail.name(), "Whole Milk");
        assertEquals(detail.meal(), "Breakfast");
        assertEquals(detail.portion(), "1 cup");
        assertEquals(detail.kcal(), 150, "calories");
        assertEquals(detail.macroGrams("Carbs"), 12.0, 0.001, "carbs");

        detail.back();
        assertTrue(home.isShowing(), "Back returns to Home");
    }

    @Test(groups = {"food", "smoke", "regression", "p1"},
            description = "FOOD_012 Edit servings")
    @Description("Editing Banana from 1 to 2 servings updates the detail to 210 kcal, lowers Remaining "
            + "by another 105, raises the Breakfast total by 105, and survives a relaunch")
    public void food012_editServings() {
        HomePage home = log("banana", "Banana", "Breakfast");
        int remainingAfterLog = home.remainingKcal();
        int breakfastAfterLog = home.mealCardKcal("BREAKFAST");

        FoodDetailPage detail = home.openLoggedFood("Banana");
        FoodLogSheet sheet = detail.edit();
        assertEquals(sheet.quantity(), "1", "edit opens with the logged quantity");
        detail = sheet.increase().save();

        assertEquals(detail.kcal(), 210, "detail after editing to 2 bananas");
        assertEquals(detail.portion(), "2 medium");

        home = detail.back();
        assertEquals(home.remainingKcal(), remainingAfterLog - 105, "Remaining after the edit");
        assertEquals(home.mealCardKcal("BREAKFAST"), breakfastAfterLog + 105, "Breakfast total after the edit");

        driver.terminateApp(Config.bundleId());
        driver.activateApp(Config.bundleId());
        home.settleAfterLog();
        assertEquals(home.remainingKcal(), remainingAfterLog - 105, "edit survives a relaunch");
        assertTrue(home.loggedRowLabel("Banana").contains(", 210, "), "Banana row shows 210 after relaunch");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_013 Edit meal type")
    @Description("Moving Apple from Snack to Lunch moves it to the Lunch card; Remaining is unchanged")
    public void food013_editMealType() {
        HomePage home = log("apple", "Apple", "Snack");
        int remainingAfterLog = home.remainingKcal();

        FoodDetailPage detail = home.openLoggedFood("Apple").edit().selectMeal("Lunch").save();
        assertEquals(detail.meal(), "Lunch", "detail shows the new meal");

        home = detail.back();
        assertTrue(home.mealCardContains("LUNCH", "Apple"), "Apple in the LUNCH card");
        assertFalse(home.mealCardContains("SNACK", "Apple"), "Apple no longer in SNACK");
        assertEquals(home.remainingKcal(), remainingAfterLog, "changing the meal does not change calories");
    }

    @Test(groups = {"food", "smoke", "regression", "p1"},
            description = "FOOD_014 Delete a logged food")
    @Description("Trash → Delete removes Egg (Boiled) and gives its 78 kcal back to Remaining")
    public void food014_deleteLoggedFood() {
        HomePage home = log("egg", "Egg (Boiled)", "Dinner");
        int remainingAfterLog = home.remainingKcal();

        home = home.openLoggedFood("Egg (Boiled)").tapTrash().confirmDelete();
        assertFalse(home.hasLoggedFood("Egg (Boiled)"), "Egg (Boiled) removed from the timeline");
        assertEquals(home.remainingKcal(), remainingAfterLog + 78, "Remaining gets the 78 kcal back");
    }

    @Test(groups = {"food", "regression", "p1"},
            description = "FOOD_015 Cancel delete keeps the food")
    @Description("Trash → Cancel closes the alert, stays on the detail, and keeps the entry")
    public void food015_cancelDeleteKeepsFood() {
        HomePage home = log("orange juice", "Orange Juice", "Snack");
        int remainingAfterLog = home.remainingKcal();

        FoodDetailPage detail = home.openLoggedFood("Orange Juice").tapTrash().cancelDelete();
        assertFalse(detail.isDeleteAlertShowing(), "alert closed");
        assertTrue(detail.isShowing(), "still on the food detail");

        home = detail.back();
        assertTrue(home.hasLoggedFood("Orange Juice"), "Orange Juice still logged");
        assertEquals(home.remainingKcal(), remainingAfterLog, "Remaining unchanged");
    }

    @Test(groups = {"food", "regression", "p2"},
            description = "FOOD_016 Cancel edit keeps values")
    @Description("Changing Rice to 3 servings and cancelling leaves the entry at 1 serving, 206 kcal")
    public void food016_cancelEditKeepsValues() {
        HomePage home = log("rice", "Rice (White, Cooked)", "Lunch");
        int remainingAfterLog = home.remainingKcal();

        FoodLogSheet sheet = home.openLoggedFood("Rice (White, Cooked)").edit();
        sheet.increase().increase();
        assertEquals(sheet.kcal(), 618, "the sheet previews 3 servings");
        FoodDetailPage detail = sheet.cancelEdit();

        assertEquals(detail.kcal(), 206, "detail unchanged after Cancel");
        assertEquals(detail.portion(), "1 cup");
        home = detail.back();
        assertEquals(home.remainingKcal(), remainingAfterLog, "Remaining unchanged");
    }

    @Test(groups = {"food", "regression", "p2"},
            description = "FOOD_017 Deleting every food empties the day")
    @Description("After every entry of the day is deleted, Home shows 'No meals logged yet' "
            + "and Remaining equals the daily goal again")
    public void food017_deleteEverythingEmptiesTheDay() {
        HomePage home = log("almonds", "Almonds", "Snack");

        home = home.deleteEveryLoggedFood();
        assertTrue(home.loggedRowLabels().isEmpty(), "no entries left");
        assertTrue(home.scrollIntoView(com.portfolio.ios.pages.Locators.staticText("No meals logged yet")) != null,
                "empty-day message is back");
        home.scrollToTop();
        assertEquals(home.remainingKcal(), home.goalKcal(), "Remaining equals the goal on an empty day");
    }

    @Test(groups = {"food", "regression", "p3"},
            description = "FOOD_018 Zero quantity cannot be saved")
    @Description("Editing a logged food to quantity 0 disables Save; Cancel leaves it unchanged")
    public void food018_zeroQuantityCannotBeSaved() {
        HomePage home = log("peanut butter", "Peanut Butter", "Snack");

        FoodLogSheet sheet = home.openLoggedFood("Peanut Butter").edit().setQuantity("0");
        assertFalse(sheet.isSaveEnabled(), "Save must be disabled at quantity 0");

        FoodDetailPage detail = sheet.cancelEdit();
        assertEquals(detail.kcal(), 190, "entry unchanged");
        detail.back();
    }
}
