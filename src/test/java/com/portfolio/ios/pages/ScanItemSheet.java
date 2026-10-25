package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * "Edit item" / "Add item" over a scan result ({@code EditItemSheet} in
 * {@code ScanResultView.swift}): name, portion (− value unit +, steps of
 * 0.5, minimum 0.5), Protein / Carbs / Fat in grams, and a read-only
 * Calories row further down.
 *
 * <p>From the app's source: calories show the scan's own figure until
 * anything is changed, then {@code round(4·P + 4·C + 9·F)} of the values on
 * screen — and that is what is saved. Changing the portion rescales the
 * three macros. Save is disabled while that sum is 0 ("Enter at least one
 * macro so we can calculate calories.") and, when adding, while the name is
 * empty.
 *
 * <p>The sheet's text fields have no labels; in tree order they are name,
 * portion, protein, carbs, fat.
 */
public final class ScanItemSheet extends BasePage {

    public static final String NEED_A_MACRO = "Enter at least one macro so we can calculate calories.";

    private static final int NAME = 0;
    private static final int PORTION = 1;
    private static final int PROTEIN = 2;
    private static final int CARBS = 3;
    private static final int FAT = 4;

    private String title;

    public ScanItemSheet(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    ScanItemSheet waitUntilLoaded(String title) {
        this.title = title;
        visible(Locators.staticText(title), Waits.SHORT, "'" + title + "' sheet");
        Waits.until(Waits.SHORT, "5 fields on the item sheet", () -> fields().size() >= 5 ? Boolean.TRUE : null);
        return this;
    }

    private List<WebElement> fields() {
        return driver.findElements(Locators.ofType("XCUIElementTypeTextField"));
    }

    private String field(int index) {
        return Waits.resilientGet(() -> {
            String value = fields().get(index).getAttribute("value");
            return value == null ? "" : value;
        });
    }

    private void setField(int index, String text) {
        WebElement field = fields().get(index);
        Waits.resilient(field::click);
        Waits.resilient(field::clear);
        Waits.resilient(() -> fields().get(index).sendKeys(text));
    }

    // ---------------------------------------------------------------- reading

    public String name() {
        return field(NAME);
    }

    public double portion() {
        return Double.parseDouble(field(PORTION));
    }

    public double protein() {
        return parseGrams(field(PROTEIN));
    }

    public double carbs() {
        return parseGrams(field(CARBS));
    }

    public double fat() {
        return parseGrams(field(FAT));
    }

    /** A macro field shows "0" as its placeholder when empty. */
    private static double parseGrams(String text) {
        return text.isBlank() || "0".equals(text) ? 0 : Double.parseDouble(text);
    }

    /** The Calories row's number; it sits below the fields, so the form is scrolled to it. */
    public int calories() {
        if (!Waits.isPresent(driver, Locators.staticText("Calories"))) {
            scrollFormDown();
        }
        List<String> texts = new java.util.ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        int at = texts.lastIndexOf("Calories");
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No Calories row on the item sheet; texts: " + texts);
        }
        return digits(texts.get(at + 1));
    }

    public boolean isSaveEnabled() {
        String save = "Add item".equals(title) ? "Add" : "Save";
        // By name: the portion "+" is also labelled "Add".
        return Waits.resilientGet(() -> driver.findElement(Locators.visibleButtonNamed(save)).isEnabled());
    }

    /** The hint is the form's last section: built only once scrolled to, and a Label, not a plain text. */
    public boolean showsNeedAMacro() {
        scrollFormDown();
        return Waits.isPresent(driver, com.portfolio.ios.core.IosBy.predicate("label CONTAINS "
                + "'Enter at least one macro so we can calculate calories.'"));
    }

    /** Scrolls the sheet's form to its end (the keyboard is put away first; it covers the bottom). */
    private void scrollFormDown() {
        hideKeyboard();
        List<WebElement> forms = driver.findElements(Locators.visibleOfType("XCUIElementTypeCollectionView"));
        if (!forms.isEmpty()) {
            driver.executeScript("mobile: scroll", Map.of(
                    "elementId", com.portfolio.ios.core.DriverFactory.elementId(forms.get(forms.size() - 1)),
                    "direction", "down"));
        }
    }

    // ---------------------------------------------------------------- changing

    @Step("Item name: {name}")
    public ScanItemSheet setName(String name) {
        setField(NAME, name);
        return this;
    }

    /** "+" or "−" on the portion: one step of 0.5. */
    @Step("Portion: one step {direction}")
    public ScanItemSheet stepPortion(String direction) {
        double before = portion();
        tap(Locators.visibleButtonNamed("up".equals(direction) ? "plus.circle" : "minus.circle"), "portion stepper");
        Waits.untilTrue(Waits.SHORT, "portion to change from " + before, () -> portion() != before);
        return this;
    }

    @Step("Macros: P {protein} g, C {carbs} g, F {fat} g")
    public ScanItemSheet setMacros(String protein, String carbs, String fat) {
        setField(PROTEIN, protein);
        setField(CARBS, carbs);
        setField(FAT, fat);
        return this;
    }

    @Step("Save the item")
    public ScanResultPage save() {
        hideKeyboard();
        String save = "Add item".equals(title) ? "Add" : "Save";
        tap(Locators.visibleButtonNamed(save), "item sheet");
        waitForGone(Locators.staticText(title), Waits.SHORT);
        return new ScanResultPage(driver, factory);
    }

    @Step("Cancel the item sheet")
    public ScanResultPage cancel() {
        hideKeyboard();
        tap(Locators.visibleButton("Cancel"), "item sheet");
        waitForGone(Locators.staticText(title), Waits.SHORT);
        return new ScanResultPage(driver, factory);
    }
}
