package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scan result ({@code Features/Scan/ScanResultView.swift}).
 *
 * <p>Verified on the simulator, top to bottom: the meal title and "N items ·
 * each will be logged separately" (one item: "Tap the card below to edit");
 * the meal chip ("Lunch, AUTO, Tap to change"); the summary ring (total
 * calories over "Cal", with Protein / Carbs / Fat as "13.6g"); optional tips;
 * "Health Rating" "7 / 10"; "Items" "(2)" and one card per item — name,
 * calories, portion, "kcal", then "P" "12g" "C" "1g" "F" "10g" and the item's
 * meal button. The bottom bar holds "Edit" (→ "Done") and "Log" / "Log N
 * items". In edit mode each card gets a pencil ("Edit") and a trash button
 * and an "Add Item" button appears.
 *
 * <p>Logging saves locally and returns to Home.
 */
public final class ScanResultPage extends BasePage {

    private static final Pattern ITEMS_SUBTITLE = Pattern.compile("(\\d+) items · each will be logged separately");
    private static final String ONE_ITEM_SUBTITLE = "Tap the card below to edit";
    private static final Pattern GRAMS = Pattern.compile("(\\d+(?:\\.\\d+)?)g");
    private static final List<String> MEALS = List.of("Breakfast", "Lunch", "Dinner", "Snack");

    /** One item card as shown. Macros are whole grams on the card. */
    public record Item(String name, int kcal, String portion, int proteinG, int carbsG, int fatG, String meal) {
    }

    public ScanResultPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    public ScanResultPage waitUntilLoaded() {
        visible(Locators.scanLogButton(), Waits.MEDIUM, "scan result's Log button");
        return this;
    }

    // ---------------------------------------------------------------- reading

    /** The title above the subtitle: the AI's meal title or one built from the item names. */
    public String title() {
        List<String> texts = texts();
        for (int i = 1; i < texts.size(); i++) {
            if (isSubtitle(texts.get(i))) {
                return texts.get(i - 1);
            }
        }
        throw new AssertionError("No result subtitle; texts: " + texts);
    }

    public String subtitle() {
        return texts().stream().filter(ScanResultPage::isSubtitle).findFirst()
                .orElseThrow(() -> new AssertionError("No result subtitle; texts: " + texts()));
    }

    /** Count shown next to "Items" / "Item": "(2)" → 2. */
    public int itemCountShown() {
        List<String> texts = texts();
        for (int i = 0; i + 1 < texts.size(); i++) {
            if (("Items".equals(texts.get(i)) || "Item".equals(texts.get(i))) && texts.get(i + 1).matches("\\(\\d+\\)")) {
                return digits(texts.get(i + 1));
            }
        }
        throw new AssertionError("No item count; texts: " + texts);
    }

    /** The summary ring's total: the number just before "Cal". */
    public int totalKcal() {
        List<String> texts = texts();
        int cal = texts.indexOf("Cal");
        for (int i = cal - 1; i >= 0; i--) {
            if (texts.get(i).matches("\\d+")) {
                return Integer.parseInt(texts.get(i));
            }
        }
        throw new AssertionError("No total calories before 'Cal'; texts: " + texts);
    }

    /** Summary grams for "Protein", "Carbs" or "Fat" ("13.6g" → 13.6). */
    public double totalGrams(String macro) {
        List<String> texts = texts();
        int at = texts.indexOf(macro);
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No summary " + macro + "; texts: " + texts);
        }
        Matcher m = GRAMS.matcher(texts.get(at + 1));
        if (!m.matches()) {
            throw new AssertionError("Summary " + macro + " is not grams: " + texts.get(at + 1));
        }
        return Double.parseDouble(m.group(1));
    }

    /** "7 / 10" → 7. */
    public int healthRating() {
        return texts().stream().filter(t -> t.matches("\\d+ / 10")).findFirst()
                .map(t -> Integer.parseInt(t.substring(0, t.indexOf(' '))))
                .orElseThrow(() -> new AssertionError("No Health Rating; texts: " + texts()));
    }

    /** The meal chip's label, "Lunch, AUTO, Tap to change". */
    public String mealChip() {
        return Waits.resilientGet(() -> visible(Locators.scanMealChip(), Waits.SHORT, "meal chip")
                .getAttribute("label"));
    }

    /** Label of the Log button: "Log" or "Log 2 items". */
    public String logButtonLabel() {
        return Waits.resilientGet(() -> visible(Locators.scanLogButton(), Waits.SHORT, "Log button")
                .getAttribute("label"));
    }

    public boolean isEditing() {
        return Waits.isPresent(driver, Locators.staticText("EDITING"));
    }

    /**
     * Every item card, in order. A card's texts are name, calories, portion,
     * "kcal", "P", grams, "C", grams, "F", grams; its meal button follows.
     */
    public List<Item> items() {
        List<String[]> elements = elementsInOrder();
        List<Item> items = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Integer> textAt = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if ("XCUIElementTypeStaticText".equals(elements.get(i)[0])) {
                texts.add(elements.get(i)[1]);
                textAt.add(i);
            }
        }
        for (int t = 3; t + 6 < texts.size(); t++) {
            if (!"kcal".equals(texts.get(t)) || !texts.get(t - 2).matches("\\d+")
                    || !"P".equals(texts.get(t + 1)) || !"C".equals(texts.get(t + 3)) || !"F".equals(texts.get(t + 5))) {
                continue;
            }
            // The card's meal menu; in edit mode its pencil and trash buttons sit around it.
            String meal = null;
            for (int e = textAt.get(t + 6) + 1; e < elements.size() && meal == null; e++) {
                if ("XCUIElementTypeButton".equals(elements.get(e)[0]) && MEALS.contains(elements.get(e)[1])) {
                    meal = elements.get(e)[1];
                }
            }
            items.add(new Item(texts.get(t - 3), Integer.parseInt(texts.get(t - 2)), texts.get(t - 1),
                    digits(texts.get(t + 2)), digits(texts.get(t + 4)), digits(texts.get(t + 6)), meal));
        }
        return items;
    }

    private List<String> texts() {
        List<String> texts = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        return texts;
    }

    private static boolean isSubtitle(String text) {
        return ONE_ITEM_SUBTITLE.equals(text) || ITEMS_SUBTITLE.matcher(text).matches();
    }

    // ---------------------------------------------------------------- meal

    /** The meal chip's menu: applies {@code meal} to every item. */
    @Step("Set every item's meal to {meal}")
    public ScanResultPage chooseMealForAll(String meal) {
        tap(Locators.scanMealChip(), "meal chip");
        WebElement option = Waits.until(Waits.SHORT, "meal menu item " + meal, () -> {
            for (WebElement button : driver.findElements(Locators.visibleOfType("XCUIElementTypeButton"))) {
                String label = Waits.resilientGet(() -> button.getAttribute("label"));
                // "Lunch  (suggested now)" for the meal the time of day suggests.
                if (label != null && (label.equals(meal) || label.startsWith(meal + "  ("))) {
                    return button;
                }
            }
            return null;
        });
        Waits.resilient(option::click);
        Waits.untilTrue(Waits.SHORT, "meal chip shows " + meal, () -> mealChip().startsWith(meal + ","));
        return this;
    }

    // ---------------------------------------------------------------- editing

    @Step("Edit the result")
    public ScanResultPage edit() {
        tap(Locators.visibleButton("Edit"), "result bottom bar");
        // Two badges: in the title block (often scrolled away) and beside "Items".
        Waits.untilTrue(Waits.SHORT, "EDITING badge", this::isEditing);
        return this;
    }

    @Step("Finish editing")
    public ScanResultPage done() {
        tap(Locators.visibleButton("Done"), "result bottom bar");
        Waits.untilTrue(Waits.SHORT, "EDITING badge gone", () -> !isEditing());
        return this;
    }

    /** Pencil of the {@code index}-th card (0-based), in edit mode. */
    @Step("Edit item {index}")
    public ScanItemSheet editItem(int index) {
        tapNth(Locators.buttonNamed("pencil.circle.fill"), index, "item pencil");
        return new ScanItemSheet(driver, factory).waitUntilLoaded("Edit item");
    }

    /** Trash of the {@code index}-th card (0-based), in edit mode. */
    @Step("Remove item {index}")
    public ScanResultPage removeItem(int index) {
        int before = items().size();
        tapNth(Locators.buttonNamed("trash.circle.fill"), index, "item trash");
        Waits.untilTrue(Waits.SHORT, "one item fewer", () -> items().size() == before - 1);
        return this;
    }

    @Step("Add an item")
    public ScanItemSheet addItem() {
        WebElement add = scrollIntoView(Locators.visibleButton("Add Item"), "Add Item");
        Waits.resilient(add::click);
        return new ScanItemSheet(driver, factory).waitUntilLoaded("Add item");
    }

    private void tapNth(org.openqa.selenium.By by, int index, String what) {
        WebElement target = Waits.until(Waits.SHORT, what + " #" + index, () -> {
            List<WebElement> found = driver.findElements(by);
            return found.size() > index ? found.get(index) : null;
        });
        if (!Waits.resilientGet(target::isDisplayed)) {
            scrollIntoView(by, what);
            target = driver.findElements(by).get(index);
        }
        Waits.resilient(target::click);
    }

    // ---------------------------------------------------------------- leaving

    /** "Log" / "Log N items": saves and returns to Home. */
    @Step("Log the scan")
    public HomePage log() {
        tap(Locators.scanLogButton(), "result bottom bar");
        waitForGone(Locators.scanLogButton(), Waits.MEDIUM);
        return new HomePage(driver, factory).waitUntilLoaded().settleAfterLog();
    }

    /** The back arrow: back to the Scanner without logging. */
    @Step("Back to the Scanner")
    public ScannerPage back() {
        tap(Locators.visibleButtonNamed("chevron.left"), "result back");
        waitForGone(Locators.scanLogButton(), Waits.SHORT);
        return new ScannerPage(driver, factory);
    }
}
