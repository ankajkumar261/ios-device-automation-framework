package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Log activities" sheet: search, "Describe", "Recent" and the A–Z catalog
 * ({@code Features/Exercise/LogActivitiesSheet.swift}). Opened by Home's
 * Activities "Add" and by "Choose activity" on Log Exercise.
 *
 * <p>Verified on the simulator: rows are Buttons labelled with the activity
 * name; the Describe row reads "Describe, Write your own — …"; section
 * headers are texts "Recent", "A", "B", …; the search field's clear button is
 * labelled "Close". A search with no hits shows an empty list, no message.
 */
public final class ActivityCatalogSheet extends BasePage {

    private static final String DESCRIBE_ROW_PREFIX = "Describe, ";

    public ActivityCatalogSheet(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the Log activities sheet")
    public ActivityCatalogSheet waitUntilLoaded() {
        visible(Locators.staticText("Log activities"), Waits.MEDIUM, "'Log activities' sheet");
        return this;
    }

    /** Activity rows on screen (catalog and Recent), without the Describe row. */
    public List<String> listedActivities() {
        List<String> names = new ArrayList<>();
        for (WebElement row : driver.findElements(Locators.activityListRows())) {
            String label = Waits.resilientGet(() -> row.getAttribute("label"));
            if (!label.startsWith(DESCRIBE_ROW_PREFIX)) {
                names.add(label);
            }
        }
        return names;
    }

    public boolean hasDescribeRow() {
        return listedRowLabels().stream().anyMatch(label -> label.startsWith(DESCRIBE_ROW_PREFIX));
    }

    /** Names in the "Recent" section (empty when it is not shown). */
    public List<String> recent() {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf("Recent");
        List<String> names = new ArrayList<>();
        for (int i = at + 1; at >= 0 && i < texts.size() && !texts.get(i).matches("[A-Z]"); i++) {
            names.add(texts.get(i));
        }
        return names;
    }

    /** Types {@code text} into the search field and waits for the list to filter. */
    @Step("Search activities for \"{text}\"")
    public ActivityCatalogSheet search(String text) {
        WebElement field = visible(Locators.visibleTextField(), Waits.SHORT, "activity search field");
        typeInto(field, text);
        String needle = text.toLowerCase(Locale.ROOT);
        Waits.untilTrue(Waits.SHORT, "activity list to filter by '" + text + "'",
                () -> !hasDescribeRow() && listedActivities().stream()
                        .allMatch(name -> name.toLowerCase(Locale.ROOT).contains(needle)));
        return this;
    }

    @Step("Clear the activity search")
    public ActivityCatalogSheet clearSearch() {
        tap(Locators.visibleButton("Close"), "activity search clear");
        Waits.untilTrue(Waits.SHORT, "Describe row to come back", this::hasDescribeRow);
        return this;
    }

    /** Taps an activity; its duration sheet opens. */
    @Step("Pick {name}")
    public ActivitySheet pick(String name) {
        tap(Locators.visibleButton(name), "activity row");
        return new ActivitySheet(driver, factory).waitUntilLoaded();
    }

    @Step("Open Describe")
    public ActivitySheet describe() {
        WebElement row = Waits.until(Waits.SHORT, "Describe row", () -> {
            for (WebElement candidate : driver.findElements(Locators.activityListRows())) {
                if (candidate.getAttribute("label").startsWith(DESCRIBE_ROW_PREFIX)) {
                    return candidate;
                }
            }
            return null;
        });
        Waits.resilient(row::click);
        return new ActivitySheet(driver, factory).waitUntilLoaded();
    }

    @Step("Close the Log activities sheet")
    public void done() {
        tap(Locators.visibleButton("Done"), "Log activities");
        waitForGone(Locators.staticText("Log activities"), Waits.SHORT);
    }

    private List<String> listedRowLabels() {
        List<String> labels = new ArrayList<>();
        for (WebElement row : driver.findElements(Locators.activityListRows())) {
            labels.add(Waits.resilientGet(() -> row.getAttribute("label")));
        }
        return labels;
    }
}
