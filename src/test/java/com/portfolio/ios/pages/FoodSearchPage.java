package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * The "Add Food" search screen ({@code Features/Scan/ManualSearchView.swift}),
 * reached from Home via "+" → "Search".
 *
 * <p>Behaviour that shapes this class, verified on the simulator:
 * <ul>
 *   <li>The built-in foods are hard-coded in the app, so their names and
 *       values are deterministic. Every query also calls the backend, and the
 *       app shows local hits only once that call returns.</li>
 *   <li>Some built-in foods are listed twice ("banana" shows two identical
 *       Banana rows); tests must not treat that as a failure.</li>
 *   <li>While the search bar is active, the screen's Cancel is hidden and the
 *       bar shows "close" instead.</li>
 * </ul>
 */
public final class FoodSearchPage extends BasePage {

    /** The diet pills, left to right. */
    public static final List<String> DIET_PILLS = List.of("Veg", "Egg", "Fish", "Non-Veg", "Vegan");

    public FoodSearchPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for Add Food")
    public FoodSearchPage waitUntilLoaded() {
        visible(Locators.foodSearchTitle(), Waits.MEDIUM, "Add Food screen title");
        return this;
    }

    public boolean isShowing() {
        return Waits.isPresent(driver, Locators.foodSearchTitle());
    }

    public boolean isDietPillShown(String label) {
        return Waits.isPresent(driver, Locators.dietPill(label));
    }

    /**
     * Types {@code query} into the search bar (clearing any previous query)
     * and waits until the screen shows a result: local hits, the "No Results"
     * state, or the "filtered out" state.
     */
    @Step("Search foods for \"{query}\"")
    public FoodSearchPage search(String query) {
        if (Waits.isPresent(driver, Locators.button("Clear text"))) {
            tap(Locators.button("Clear text"), "clear the previous search");
        }
        WebElement field = visible(Locators.foodSearchField(), Waits.SHORT, "food search bar");
        typeInto(field, query);
        Waits.untilTrue(Waits.MEDIUM, "search results for \"" + query + "\" (the app waits for the "
                        + "backend before showing even built-in foods)",
                () -> Waits.isPresent(driver, Locators.staticText("Local"))
                        || Waits.isPresent(driver, Locators.foodNoResults())
                        || Waits.isPresent(driver, Locators.foodFilteredOut()));
        return this;
    }

    /** True when a row named exactly {@code name} is on screen. */
    public boolean isListed(String name) {
        return Waits.isPresent(driver, Locators.foodRow(name));
    }

    /** Number of visible built-in rows whose label contains {@code fragment}, e.g. ", Egg (". */
    public int localRowsContaining(String fragment) {
        int count = 0;
        for (WebElement row : driver.findElements(Locators.localFoodRows(fragment))) {
            if (Waits.resilientGet(row::isDisplayed)) {
                count++;
            }
        }
        return count;
    }

    /** Full accessibility label of the first visible row for {@code name}. */
    public String rowLabel(String name) {
        return Waits.resilientGet(() -> firstVisibleRow(name).getAttribute("label"));
    }

    /**
     * The first <i>visible</i> row for {@code name}. Once a food is logged,
     * Home's timeline (still in the tree beneath this screen, hidden) has a row
     * with the same label pattern — and it comes first in the tree — so taking
     * the first match would wait on a row nobody can see.
     */
    private WebElement firstVisibleRow(String name) {
        return Waits.until(Waits.SHORT, "visible search row " + name, () -> {
            for (WebElement row : driver.findElements(Locators.foodRow(name))) {
                if (row.isDisplayed()) {
                    return row;
                }
            }
            return null;
        });
    }

    public boolean showsNoResults() {
        return Waits.isPresent(driver, Locators.foodNoResults());
    }

    public boolean showsFilteredOut() {
        return Waits.isPresent(driver, Locators.foodFilteredOut());
    }

    @Step("Toggle the {label} diet pill")
    public FoodSearchPage toggleDietPill(String label) {
        tap(Locators.dietPill(label), "diet pill");
        return this;
    }

    /** Opens the log sheet for the first row named {@code name}. */
    @Step("Open the log sheet for {name}")
    public FoodLogSheet open(String name) {
        WebElement row = firstVisibleRow(name);
        Waits.resilient(row::click);
        return new FoodLogSheet(driver, factory).waitUntilLoaded();
    }

    /** Leaves Add Food without logging: ends an active search, then Cancel. */
    @Step("Close Add Food")
    public void close() {
        if (Waits.isPresent(driver, Locators.button("close"))) {
            tap(Locators.button("close"), "end the active search");
        }
        tap(Locators.button("Cancel"), "Add Food Cancel");
        waitForGone(Locators.foodSearchTitle(), Waits.SHORT);
    }
}
