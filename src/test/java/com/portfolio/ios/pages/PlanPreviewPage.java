package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A plan's preview, opened from its card ({@code DietCategoryDetailView.swift}).
 * Everything on it comes from the app's bundled {@code diet_catalog.json}.
 *
 * <p>Verified on the simulator: name, "1700–2200 kcal/day", "7-day plan",
 * "About this plan" with "See more" / "See less", "Meals Preview" with day
 * Buttons "Today", "Day 2" … "Day 7" (by design they don't select a day —
 * they draw attention to "Generate Plan"), today's meals as Others
 * "Breakfast. Greek Yogurt with Berries. 240 kcal", then "Choose this plan
 * if…", "Recommended for", "Not recommended for", "Sources & References";
 * "Back" and "Generate Plan".
 */
public final class PlanPreviewPage extends BasePage {

    /** One of today's preview meals. */
    public record PreviewMeal(String slot, String name, int kcal) {
    }

    public PlanPreviewPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the plan preview")
    public PlanPreviewPage waitUntilLoaded() {
        visible(Locators.staticText("About this plan"), Waits.MEDIUM, "plan preview 'About this plan'");
        return this;
    }

    /** The first text of the screen: the plan's name. */
    public String name() {
        return visibleTextsInOrder().get(0);
    }

    /** "1700–2200 kcal/day". */
    public String kcalRange() {
        for (String text : visibleTextsInOrder()) {
            if (text.endsWith(" kcal/day")) {
                return text;
            }
        }
        throw new AssertionError("No kcal/day chip on the preview: " + visibleTextsInOrder());
    }

    public boolean showsText(String label) {
        return Waits.isPresent(driver, Locators.staticText(label));
    }

    /** Labels of the day buttons, "Today", "Day 2" … "Day 7". */
    public List<String> dayButtons() {
        List<String> days = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeButton".equals(element[0])
                    && (element[1].equals("Today") || element[1].matches("Day [0-9]"))) {
                days.add(element[1]);
            }
        }
        return days;
    }

    @Step("Tap {day} on the preview")
    public PlanPreviewPage tapDay(String day) {
        tap(Locators.visibleButton(day), "preview day");
        return this;
    }

    /** Today's meals, in slot order. */
    public List<PreviewMeal> todaysMeals() {
        List<PreviewMeal> meals = new ArrayList<>();
        for (WebElement card : driver.findElements(Locators.planPreviewMeals())) {
            String label = Waits.resilientGet(() -> card.getAttribute("label"));
            String slot = label.substring(0, label.indexOf(". "));
            String rest = label.substring(slot.length() + 2);
            String name = rest.substring(0, rest.lastIndexOf(". "));
            int kcal = digits(rest.substring(rest.lastIndexOf(". ") + 2));
            meals.add(new PreviewMeal(slot, name, kcal));
        }
        return meals;
    }

    /** Lines under a section heading, up to the next heading. */
    public List<String> sectionLines(String heading, String nextHeading) {
        List<String> lines = new ArrayList<>();
        boolean in = false;
        for (String[] element : elementsInOrder()) {
            if (!"XCUIElementTypeStaticText".equals(element[0])) {
                continue;
            }
            if (element[1].equals(heading)) {
                in = true;
            } else if (element[1].equals(nextHeading)) {
                return lines;
            } else if (in) {
                lines.add(element[1]);
            }
        }
        return lines;
    }

    /** "See more" ↔ "See less" on "About this plan"; returns the new label. */
    @Step("Toggle the plan description")
    public String toggleDescription() {
        String from = Waits.isPresent(driver, Locators.visibleButton("See more")) ? "See more" : "See less";
        String to = from.equals("See more") ? "See less" : "See more";
        tap(Locators.visibleButton(from), "About this plan");
        visible(Locators.visibleButton(to), Waits.SHORT, "'" + to + "' after tapping '" + from + "'");
        return to;
    }

    public boolean showsGeneratePlan() {
        return Waits.isPresent(driver, Locators.visibleButton("Generate Plan"));
    }

    @Step("Back to the plan list")
    public MealPlansPage back() {
        tap(Locators.visibleButton("Back"), "plan preview");
        waitForGone(Locators.staticText("About this plan"), Waits.SHORT);
        return new MealPlansPage(driver, factory);
    }
}
