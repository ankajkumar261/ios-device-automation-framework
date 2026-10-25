package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Intermittent Fasting" hub, opened from Home's tile once there is a
 * plan or any fast ({@code Features/Fasting/FastingView.swift}).
 *
 * <p>Verified on the simulator, texts in order: "Current Fast" (while one
 * runs); "Your Plan", "16:8", "Fast 1:32 PM → 5:32 AM", "Every day",
 * "Next fast Tue, Oct 6, 1:32 PM" with "Update" / "Delete"; "Recent Fasts"
 * then per fast "Oct 5, 2026", "2m", "Ended early" (or the empty line);
 * bottom button "Edit Fast Schedule" or "Start New Fast". A history row is
 * deleted from its long-press menu ("Delete Fast"); both delete
 * confirmations are action sheets.
 */
public final class FastingHubPage extends BasePage {

    public static final String HISTORY_EMPTY = "Your completed fasts will show up here.";
    public static final String DELETE_PLAN_MESSAGE = "Delete your fasting plan? Your past fasts stay in Insights.";
    public static final String DELETE_FAST_MESSAGE =
            "Delete this fast? It will also disappear from your Insights chart.";

    /** One "Recent Fasts" row. */
    public record HistoryRow(String date, String length, String status) {
    }

    public FastingHubPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the fasting hub")
    public FastingHubPage waitUntilLoaded() {
        visible(Locators.staticText("Recent Fasts"), Waits.MEDIUM, "fasting hub 'Recent Fasts'");
        return this;
    }

    public boolean hasPlan() {
        return Waits.isPresent(driver, Locators.staticText("Your Plan"));
    }

    public boolean hasCurrentFast() {
        return Waits.isPresent(driver, Locators.staticText("Current Fast"));
    }

    /** "16:8", or "6h" for a custom plan. */
    public String planProtocol() {
        return textAfter("Your Plan", 1);
    }

    /** "Fast 1:32 PM → 5:32 AM". */
    public String planWindow() {
        return FastingText.normalise(textAfter("Your Plan", 2));
    }

    /** "Every day", "Just today" or the weekdays, "Tue". */
    public String planRepeat() {
        return textAfter("Your Plan", 3);
    }

    /** "Next fast Tue, Oct 6, 1:32 PM" or "No upcoming fast scheduled". */
    public String planNextFast() {
        return FastingText.normalise(textAfter("Your Plan", 4));
    }

    /** "Edit Fast Schedule" or "Start New Fast". */
    public String bottomButton() {
        for (String label : List.of("Edit Fast Schedule", "Start New Fast")) {
            if (Waits.isPresent(driver, Locators.visibleButton(label))) {
                return label;
            }
        }
        return null;
    }

    public List<HistoryRow> history() {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf("Recent Fasts");
        List<HistoryRow> rows = new ArrayList<>();
        for (int i = at + 1; at >= 0 && i + 2 < texts.size(); i += 3) {
            String status = texts.get(i + 2);
            if (!status.equals("Ended early") && !status.equals("Completed")) {
                break;
            }
            rows.add(new HistoryRow(texts.get(i), FastingText.normalise(texts.get(i + 1)), status));
        }
        return rows;
    }

    public boolean historyIsEmpty() {
        return Waits.isPresent(driver, Locators.staticText(HISTORY_EMPTY));
    }

    @Step("End Fast on the hub")
    public FastingEndAlert tapEndFast() {
        tap(Locators.visibleButton("End Fast"), "fasting hub");
        return new FastingEndAlert(driver, factory).waitUntilShown();
    }

    @Step("Update the plan")
    public FastingSetupPage update() {
        tap(Locators.visibleButton("Update"), "fasting hub");
        return new FastingSetupPage(driver, factory).waitForProtocolPicker();
    }

    @Step("Tap Delete plan")
    public FastingHubPage tapDeletePlan() {
        tap(Locators.visibleButton("Delete"), "fasting hub plan");
        visible(Locators.sheetTitled(DELETE_PLAN_MESSAGE), Waits.SHORT, "delete-plan confirmation");
        return this;
    }

    /** The confirmation has no Cancel on iOS 26: tapping outside closes it. */
    @Step("Dismiss the delete-plan confirmation")
    public FastingHubPage dismissDeletePlan() {
        if (!new HomePage(driver, factory).dismissActionSheetIfOpen()) {
            throw new AssertionError("The delete-plan confirmation did not close when tapping outside it");
        }
        return this;
    }

    /** Deletes the plan; the app then opens the protocol picker (confirmed behaviour). */
    @Step("Confirm Delete plan")
    public FastingSetupPage confirmDeletePlan() {
        tap(Locators.sheetButton("Delete"), "delete-plan confirmation");
        waitForGone(Locators.sheetTitled(DELETE_PLAN_MESSAGE), Waits.SHORT);
        return new FastingSetupPage(driver, factory).waitForProtocolPicker();
    }

    /**
     * Deletes the first "Recent Fasts" row: long-press → "Delete Fast" →
     * confirm. The menu item reports itself invisible and ignores an element
     * click, so it is tapped at its own centre.
     */
    @Step("Delete the newest fast from history")
    public FastingHubPage deleteNewestFast() {
        int before = history().size();
        WebElement row = Waits.until(Waits.SHORT, "a history row", () -> {
            for (String status : List.of("Ended early", "Completed")) {
                List<WebElement> found = driver.findElements(Locators.staticText(status));
                for (WebElement candidate : found) {
                    if (candidate.isDisplayed()) {
                        return candidate;
                    }
                }
            }
            return null;
        });
        openMenuAndTapDelete(row);
        tap(Locators.sheetButton("Delete Fast"), "delete-fast confirmation");
        waitForGone(Locators.sheetTitled(DELETE_FAST_MESSAGE), Waits.SHORT);
        Waits.untilTrue(Waits.SHORT, "history to shrink", () -> history().size() == before - 1);
        return this;
    }

    @Step("Close the fasting hub")
    public HomePage done() {
        tap(Locators.visibleButton("Done"), "fasting hub");
        waitForGone(Locators.staticText("Recent Fasts"), Waits.SHORT);
        return new HomePage(driver, factory).backAtTop();
    }

    private void tapCentre(Rectangle box) {
        Gestures.tapAt(driver, box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2);
    }

    /**
     * Long-presses {@code row} and taps "Delete Fast" in its menu, until the
     * delete confirmation shows (two attempts: a press during a transition
     * may not open the menu). The menu item's live reference goes stale while
     * the menu animates in, so its position is read from page-source
     * snapshots and trusted once two agree.
     */
    private void openMenuAndTapDelete(WebElement row) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            Gestures.longPress(driver, row, Duration.ofSeconds(1));
            try {
                if (!Waits.untilQuietly(Waits.SHORT,
                        () -> Waits.isPresent(driver, Locators.contextMenuPreview()))) {
                    continue;   // no menu: press again
                }
                Rectangle[] last = {null};
                Rectangle item = Waits.until(Waits.SHORT, "'Delete Fast' menu item to settle", () -> {
                    Rectangle now = boundsInSource("XCUIElementTypeButton", "Delete Fast");
                    boolean settled = now != null && now.equals(last[0]);
                    last[0] = now;
                    return settled ? now : null;
                });
                tapCentre(item);
                if (isVisible(Locators.sheetTitled(DELETE_FAST_MESSAGE), Waits.SHORT)) {
                    return;
                }
            } catch (RuntimeException | AssertionError menuFailure) {
                // Leave no menu open behind a failure: it blocks every later step.
                new HomePage(driver, factory).dismissContextMenuIfOpen();
                throw menuFailure;
            }
            new HomePage(driver, factory).dismissContextMenuIfOpen();
        }
        throw new AssertionError("The delete-fast confirmation never appeared after two long-presses");
    }

    private String textAfter(String caption, int offset) {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf(caption);
        if (at < 0 || at + offset >= texts.size()) {
            throw new AssertionError("No text " + offset + " after '" + caption + "' on the fasting hub; texts: "
                    + texts);
        }
        return texts.get(at + offset);
    }
}
