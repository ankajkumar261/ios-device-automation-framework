package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Water tracking: the Home tile and the hydration sheet it opens
 * ({@code Features/Home/WaterTracker.swift}).
 *
 * <p>Verified on the simulator: the tile shows "250/2000ml" (8 glasses of
 * 250 ml); its "−" is the only Button named "minus" on Home and is disabled
 * at 0; its two "+" buttons are named "plus" like the round create button —
 * the upper one opens the sheet, the lower one adds a glass. The sheet shows
 * "HYDRATION", the count, "/8 glasses", 8 tappable glass images, "Undo" and
 * "Add Glass", and has no close button: swiping its grabber down closes it.
 */
public final class WaterPage extends BasePage {

    public static final int ML_PER_GLASS = 250;

    private final HomePage home;

    public WaterPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
        this.home = new HomePage(driver, factory);
    }

    // ---------------------------------------------------------------- tile

    /** The tile's total text, scrolled into view, e.g. "250/2000ml". */
    public String tileTotal() {
        WebElement total = home.scrollIntoView(Locators.waterTileTotal());
        if (total == null) {
            throw new AssertionError("Water tile is not on Home");
        }
        return Waits.resilientGet(() -> total.getAttribute("label"));
    }

    /** Glasses today, from the tile's millilitres. */
    public int glasses() {
        return Integer.parseInt(tileTotal().split("/")[0]) / ML_PER_GLASS;
    }

    public boolean isRemoveEnabled() {
        home.scrollIntoView(Locators.waterTileTotal());
        return Waits.resilientGet(() -> driver.findElement(Locators.waterRemoveButton()).isEnabled());
    }

    @Step("Add a glass of water")
    public WaterPage add() {
        String before = tileTotal();
        Waits.resilient(smallPlus(false)::click);
        waitForTotalChangeFrom(before);
        return this;
    }

    @Step("Remove a glass of water")
    public WaterPage remove() {
        String before = tileTotal();
        tap(Locators.waterRemoveButton(), "water −");
        waitForTotalChangeFrom(before);
        return this;
    }

    /** Back to 0 glasses through the app's own "−" (a cheap, deterministic reset). */
    @Step("Reset water to 0 glasses")
    public WaterPage resetToZero() {
        for (int i = 0; i < 40 && glasses() > 0; i++) {
            remove();
        }
        if (glasses() != 0) {
            throw new AssertionError("Water did not reach 0; tile shows " + tileTotal());
        }
        return this;
    }

    // ---------------------------------------------------------------- sheet

    @Step("Open the water sheet")
    public WaterPage openSheet() {
        tileTotal();   // scroll the tile into view
        Waits.resilient(smallPlus(true)::click);
        visible(Locators.waterSheetTitle(), Waits.SHORT, "water sheet 'HYDRATION'");
        return this;
    }

    /** The sheet's count: the number shown just before "/8 glasses". */
    public int sheetCount() {
        List<String> texts = visibleTextsInOrder();
        for (int i = 1; i < texts.size(); i++) {
            if (texts.get(i).startsWith("/") && texts.get(i).contains("glass")) {
                return Integer.parseInt(texts.get(i - 1).trim());
            }
        }
        throw new AssertionError("No glass count on the water sheet; texts: " + texts);
    }

    @Step("Tap Add Glass")
    public WaterPage sheetAddGlass() {
        int before = sheetCount();
        tap(Locators.visibleButton("Add Glass"), "water sheet");
        Waits.untilTrue(Waits.SHORT, "sheet count to rise", () -> sheetCount() == before + 1);
        return this;
    }

    @Step("Tap Undo")
    public WaterPage sheetUndo() {
        int before = sheetCount();
        tap(Locators.visibleButton("Undo"), "water sheet");
        Waits.untilTrue(Waits.SHORT, "sheet count to fall", () -> sheetCount() == before - 1);
        return this;
    }

    /** Taps glass {@code n} (1-based, left to right); the count becomes {@code n}. */
    @Step("Tap glass {n}")
    public WaterPage sheetTapGlass(int n) {
        List<WebElement> glasses = driver.findElements(Locators.waterSheetGlasses());
        glasses.sort(Comparator.comparingInt(glass -> glass.getRect().getX()));
        if (glasses.size() < n) {
            throw new AssertionError("Water sheet shows " + glasses.size() + " glasses, wanted glass " + n);
        }
        Waits.resilient(glasses.get(n - 1)::click);
        Waits.untilTrue(Waits.SHORT, "sheet count to become " + n, () -> sheetCount() == n);
        return this;
    }

    /** Swipes the sheet's grabber down; the sheet has no close button. */
    @Step("Close the water sheet")
    public WaterPage closeSheet() {
        WebElement grabber = visible(Locators.sheetGrabber(), Waits.SHORT, "water sheet grabber");
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("mobile: swipe",
                Map.of("elementId", ((org.openqa.selenium.remote.RemoteWebElement) grabber).getId(),
                        "direction", "down"));
        waitForGone(Locators.waterSheetTitle(), Waits.SHORT);
        return this;
    }

    // ---------------------------------------------------------------- plumbing

    /** The water tile's upper (opens the sheet) or lower (adds a glass) small "+". */
    private WebElement smallPlus(boolean upper) {
        return Waits.until(Waits.SHORT, "water tile '+' (" + (upper ? "header" : "stepper") + ")", () -> {
            WebElement pick = null;
            for (WebElement plus : driver.findElements(Locators.homePlusButtons())) {
                org.openqa.selenium.Rectangle r = plus.getRect();
                if (r.getWidth() >= 50) {
                    continue;   // the round create button
                }
                if (pick == null || (upper ? r.getY() < pick.getRect().getY() : r.getY() > pick.getRect().getY())) {
                    pick = plus;
                }
            }
            return pick;
        });
    }

    private void waitForTotalChangeFrom(String before) {
        Waits.untilTrue(Waits.SHORT, "water total to change from " + before,
                () -> !before.equals(tileTotalNow()));
    }

    /** Current tile text without scrolling (null if not in the tree). */
    private String tileTotalNow() {
        List<WebElement> totals = driver.findElements(Locators.waterTileTotal());
        return totals.isEmpty() ? null : Waits.resilientGet(() -> totals.get(0).getAttribute("label"));
    }
}
