package com.portfolio.ios.tests.scan;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.fixtures.SimulatorMedia;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.ScanItemSheet;
import com.portfolio.ios.pages.ScanResultPage;
import com.portfolio.ios.pages.ScanResultPage.Item;
import com.portfolio.ios.pages.ScannerPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Module 12 — food scan: the Scanner (Home "+" → "Scan"), its result screen,
 * editing items and logging.
 *
 * <p>Getting a result needs the production food-recognition AI, so those
 * tests are in the {@code ai} group (on demand: {@code mvn test -Dgroups=ai}).
 * They check structure and arithmetic the app does locally — totals add up,
 * calories follow 4·P + 4·C + 9·F after an edit, logging moves Remaining by
 * the total — never what the AI recognised.
 *
 * <p>Where the result comes from: on the simulator (no camera, so the
 * "Camera Not Available" cover hides every mode tab) a photo of boiled eggs
 * and tomato from the photo library; on a real iPhone the Type tab with a
 * written description.
 */
@Epic("Scan")
@Feature("Food scan")
public class ScanTest extends AbstractAppTest {

    /** What the Type tab is given on a real device. */
    private static final String MEAL_DESCRIPTION = "2 boiled eggs and 100 g of sliced tomato";
    private static final String LIMIT_TITLE = "Try again tomorrow";
    private static final String LIMIT_MESSAGE = "Your daily limit has reached. Try again tomorrow.";

    @BeforeClass(alwaysRun = true)
    public void addMealPhoto() {
        SimulatorMedia.ensureMealPhoto();
    }

    private ScannerPage scanner() {
        return home().openScanner().answerCameraPromptIfAsked(true);
    }

    /** Starts the analysis of the test meal: library photo (simulator) or Type tab (device). */
    private ScannerPage submitTestMeal(ScannerPage scanner) {
        return Config.isReal() ? scanner.describeFood(MEAL_DESCRIPTION) : scanner.chooseNewestLibraryPhoto();
    }

    /** One AI call: the test meal's result screen. */
    private ScanResultPage scanTestMeal() {
        return submitTestMeal(scanner()).waitForResult();
    }

    private HomePage leave(ScanResultPage result) {
        ScannerPage scanner = result.back();
        return Config.isReal() ? scanner.back() : scanner.closeOverlay();
    }

    /** The meal the app picks for "now" ({@code FoodEntry.swift:162-171}). */
    private static String mealForHour(int hour) {
        if (hour >= 6 && hour <= 11) {
            return "Breakfast";
        }
        if (hour >= 12 && hour <= 15) {
            return "Lunch";
        }
        if (hour >= 18 && hour <= 22) {
            return "Dinner";
        }
        return "Snack";
    }

    private static int sumKcal(List<Item> items) {
        return items.stream().mapToInt(Item::kcal).sum();
    }

    // ---------------------------------------------------------------- without the AI

    @Test(groups = {"scan", "smoke", "regression", "p0"}, priority = 0,
            description = "SCAN_001 The Scanner opens from Home")
    @Description("Home '+' → 'Scan' opens the Scanner with its five modes. On the simulator, with camera access "
            + "granted but no camera, 'Camera Not Available' offers the photo library or Close, and Close returns Home")
    public void scan001_scannerOpens() {
        ScannerPage scanner = scanner();
        assertEquals(scanner.modes(), ScannerPage.MODES, "mode tabs");

        if (Config.isReal()) {
            assertFalse(scanner.showsCameraNotAvailable(), "a real iPhone has a camera");
            scanner.back();
            return;
        }
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(scanner.showsCameraNotAvailable(), "'" + ScannerPage.CAMERA_NOT_AVAILABLE + "' on the simulator");
        soft.assertEquals(scanner.overlayText(), ScannerPage.CAMERA_NOT_AVAILABLE_TEXT, "its explanation");
        soft.assertTrue(scanner.hasButton("Choose from Library"), "'Choose from Library'");
        soft.assertTrue(scanner.hasButton("Close"), "'Close'");
        soft.assertAll();
        scanner.closeOverlay();
    }

    @Test(groups = {"scan", "regression", "p1"}, priority = 1,
            description = "SCAN_002 Scanning stops at the daily limit")
    @Description("With today's limit used up, a scan ends at once on 'Try again tomorrow' — no AI call — and "
            + "Cancel goes back to the Scanner")
    public void scan002_dailyLimit() {
        // The photo counts against the "scan" bucket (40 a day), typed food against "general" (200 a day).
        String bucket = Config.isReal() ? "general" : "scan";
        relaunchAppWith(dailyLimitReached(bucket, Config.isReal() ? 200 : 40));
        try {
            ScannerPage scanner = submitTestMeal(scanner());

            List<String> card = scanner.waitForErrorCard();

            assertTrue(card.contains(LIMIT_TITLE), "error card title '" + LIMIT_TITLE + "'; texts: " + card);
            assertTrue(card.contains(LIMIT_MESSAGE), "error card message; texts: " + card);
            scanner.cancelError();
        } finally {
            relaunchApp();
        }
    }

    // ---------------------------------------------------------------- with a result (AI)

    @Test(groups = {"scan", "ai", "regression", "p1"}, priority = 2,
            description = "SCAN_003 A scan result adds up")
    @Description("The result shows a title, one card per item matching the count, a total equal to the items' "
            + "calories, today's suggested meal (AUTO) on every item, a 1–10 health rating and a matching Log button")
    public void scan003_resultAddsUp() {
        int hourBefore = LocalTime.now().getHour();
        ScanResultPage result = scanTestMeal();
        int hourAfter = LocalTime.now().getHour();

        List<Item> items = result.items();
        SoftAssert soft = new SoftAssert();
        soft.assertFalse(result.title().isBlank(), "a meal title");
        soft.assertFalse(items.isEmpty(), "at least one item card");
        soft.assertEquals(result.itemCountShown(), items.size(), "count next to Items = cards");
        soft.assertEquals(result.subtitle(), items.size() == 1 ? "Tap the card below to edit"
                : items.size() + " items · each will be logged separately", "subtitle");
        // Cards show rounded calories; the total is rounded once, so allow 1 kcal per item.
        soft.assertTrue(Math.abs(result.totalKcal() - sumKcal(items)) <= items.size(),
                "total " + result.totalKcal() + " = sum of the cards " + sumKcal(items));
        Set<String> meals = new HashSet<>(List.of(mealForHour(hourBefore), mealForHour(hourAfter)));
        String chip = result.mealChip();
        soft.assertTrue(meals.stream().anyMatch(m -> chip.equals(m + ", AUTO, Tap to change")),
                "meal chip suggests " + meals + " for now; was " + chip);
        for (Item item : items) {
            soft.assertTrue(item.kcal() > 0, item.name() + " has calories");
            soft.assertTrue(chip.startsWith(item.meal() + ","), item.name() + "'s meal " + item.meal()
                    + " matches the chip");
        }
        soft.assertTrue(result.healthRating() >= 1 && result.healthRating() <= 10,
                "health rating out of 10: " + result.healthRating());
        soft.assertEquals(result.logButtonLabel(), items.size() == 1 ? "Log" : "Log " + items.size() + " items",
                "Log button");
        soft.assertAll();

        leave(result);
    }

    @Test(groups = {"scan", "ai", "regression", "p1"}, priority = 3,
            description = "SCAN_004 Editing an item recalculates calories")
    @Description("An item opens with its scanned calories; typed macros P10 C20 F5 give 165 kcal (4/4/9 per gram), "
            + "which the card and the total take on Save; one portion step scales the macros and recalculates; "
            + "Cancel keeps the saved values")
    public void scan004_editItem() {
        ScanResultPage result = scanTestMeal().edit();
        List<Item> before = result.items();

        ScanItemSheet sheet = result.editItem(0);
        assertEquals(sheet.name(), before.get(0).name(), "the sheet edits the first card");
        assertEquals(sheet.calories(), before.get(0).kcal(), "untouched, the sheet shows the scanned calories");
        sheet.setMacros("10", "20", "5");
        assertEquals(sheet.calories(), 165, "4×10 + 4×20 + 9×5");
        result = sheet.save();

        List<Item> after = result.items();
        assertEquals(after.get(0).kcal(), 165, "card calories after Save");
        assertEquals(List.of(after.get(0).proteinG(), after.get(0).carbsG(), after.get(0).fatG()), List.of(10, 20, 5),
                "card macros after Save");
        int others = sumKcal(after) - after.get(0).kcal();
        assertTrue(Math.abs(result.totalKcal() - (165 + others)) <= after.size(),
                "total " + result.totalKcal() + " = 165 + the other items " + others);

        sheet = result.editItem(0);
        double portion = sheet.portion();
        sheet.stepPortion("up");
        double scale = sheet.portion() / portion;
        assertEquals(sheet.portion(), portion + 0.5, 0.001, "one step is 0.5");
        assertEquals(sheet.protein(), 10 * scale, 0.051, "protein scales with the portion");
        assertEquals(sheet.carbs(), 20 * scale, 0.051, "carbs scale with the portion");
        assertEquals(sheet.fat(), 5 * scale, 0.051, "fat scales with the portion");
        assertEquals(sheet.calories(),
                (int) Math.round(4 * sheet.protein() + 4 * sheet.carbs() + 9 * sheet.fat()),
                "calories from the macros on screen");
        result = sheet.cancel();
        assertEquals(result.items().get(0).kcal(), 165, "Cancel keeps the saved item");

        leave(result.done());
    }

    @Test(groups = {"scan", "ai", "regression", "p2"}, priority = 4,
            description = "SCAN_005 Adding and removing items")
    @Description("Add Item needs a name and at least one macro; 25 g carbs makes a 100 kcal item that joins the "
            + "list, the total and the Log button; removing it puts everything back")
    public void scan005_addAndRemoveItems() {
        ScanResultPage result = scanTestMeal().edit();
        List<Item> before = result.items();
        int totalBefore = result.totalKcal();

        ScanItemSheet sheet = result.addItem();
        assertFalse(sheet.isSaveEnabled(), "Add is disabled with no name and no macros");
        assertTrue(sheet.showsNeedAMacro(), "'" + ScanItemSheet.NEED_A_MACRO + "'");
        sheet.setName("Test Apple");
        assertFalse(sheet.isSaveEnabled(), "still disabled with a name but no macros");
        sheet.setMacros("0", "25", "0");
        assertEquals(sheet.calories(), 100, "4 × 25 g carbs");
        assertTrue(sheet.isSaveEnabled(), "Add is enabled once there are calories");
        result = sheet.save();

        List<Item> withApple = result.items();
        assertEquals(withApple.size(), before.size() + 1, "one more card");
        Item apple = withApple.get(withApple.size() - 1);
        assertEquals(apple.name(), "Test Apple", "the new card is last");
        assertEquals(apple.kcal(), 100, "its calories");
        assertTrue(Math.abs(result.totalKcal() - (totalBefore + 100)) <= 1, "total + 100");
        assertEquals(result.logButtonLabel(), "Log " + withApple.size() + " items", "Log button counts it");

        result.removeItem(withApple.size() - 1);
        assertEquals(result.items(), before, "removing it leaves the original cards");
        assertTrue(Math.abs(result.totalKcal() - totalBefore) <= 1, "total back to " + totalBefore);

        leave(result.done());
    }

    @Test(groups = {"scan", "ai", "regression", "p1"}, priority = 5,
            description = "SCAN_006 Logging a scan to a chosen meal")
    @Description("Choosing another meal on the chip moves every item; Log returns Home with Remaining down by the "
            + "total and the meal listed under that meal")
    public void scan006_logToChosenMeal() {
        HomePage home = home();
        int remainingBefore = home.remainingKcal();
        ScanResultPage result = scanTestMeal();
        String suggested = result.mealChip().substring(0, result.mealChip().indexOf(','));
        String meal = "Dinner".equals(suggested) ? "Breakfast" : "Dinner";

        result.chooseMealForAll(meal);
        assertEquals(result.mealChip(), meal + ", Tap to change", "the chip shows the chosen meal, no AUTO");
        List<Item> items = result.items();
        for (Item item : items) {
            assertEquals(item.meal(), meal, item.name() + " moved too");
        }
        int total = result.totalKcal();
        String title = result.title();

        home = result.log();

        assertEquals(home.remainingKcal(), remainingBefore - total, "Remaining after logging " + total + " kcal");
        assertEquals(home.mealCardKcal(meal.toUpperCase()), total, meal + " card total");
        // Several items are listed as one row titled like the result; one item under its own name.
        String name = items.size() == 1 ? items.get(0).name() : title;
        List<String> rows = home.loggedRowLabels();
        assertTrue(rows.stream().anyMatch(r -> r.contains(name) && r.contains(", " + total + ", ")),
                "a timeline row for " + name + " with " + total + " kcal; rows: " + rows);
    }
}
