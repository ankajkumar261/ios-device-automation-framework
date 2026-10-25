package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The Meals tab's list of plans, "Available Meal Plans" — the whole tab
 * before any plan exists, and the bottom of My Plan after
 * ({@code Features/Meals/MealsTabRoot.swift}).
 *
 * <p>Verified on the simulator: per card, texts "Plan Preview", the plan's
 * name ("Mediterranean Wellness"), "7 days", and a Button "Generate" — or
 * "Current plan" on the active plan, "Use this plan" on one already built.
 * The card itself is not a Button; tapping its name opens the preview.
 */
public final class MealPlansPage extends BasePage {

    public static final String LIST_HEADING = "Available Meal Plans";

    public MealPlansPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /** Opens the Meals tab: the plan list, or My Plan when a plan is active. */
    @Step("Open the Meals tab")
    public MealPlansPage openTab() {
        new HomePage(driver, factory).openTab("Meals");
        Waits.until(Waits.MEDIUM, "Meals tab", () -> Waits.isPresent(driver, Locators.staticText("Meal Plan"))
                ? Boolean.TRUE : null);
        return this;
    }

    /** True when the tab shows only the plan list (no plan active yet). */
    public boolean isPickerOnly() {
        return Waits.isPresent(driver, Locators.staticText(LIST_HEADING))
                && !Waits.isPresent(driver, Locators.visibleButton("Reset Plan"));
    }

    /** Plan names in screen order: the text after each "Plan Preview". */
    public List<String> planNames() {
        List<String> names = new ArrayList<>();
        List<String[]> elements = elementsInOrder();
        for (int i = 0; i + 1 < elements.size(); i++) {
            if (isText(elements.get(i), "Plan Preview")) {
                for (int j = i + 1; j < elements.size(); j++) {
                    if ("XCUIElementTypeStaticText".equals(elements.get(j)[0])) {
                        names.add(elements.get(j)[1]);
                        break;
                    }
                }
            }
        }
        return names;
    }

    /** Per plan, the label of its action button ("Generate", "Current plan", …). */
    public List<String> planActions() {
        List<String> actions = new ArrayList<>();
        boolean inCard = false;
        for (String[] element : elementsInOrder()) {
            if (isText(element, "Plan Preview")) {
                inCard = true;
            } else if (inCard && "XCUIElementTypeButton".equals(element[0])
                    && List.of("Generate", "Current plan", "Use this plan").contains(element[1])) {
                actions.add(element[1]);
                inCard = false;
            }
        }
        return actions;
    }

    /** "7 days" chips across the list. */
    public long durationChips() {
        return elementsInOrder().stream().filter(e -> isText(e, "7 days")).count();
    }

    @Step("Open the {name} preview")
    public PlanPreviewPage openPreview(String name) {
        WebElement title = scrollIntoView(Locators.staticText(name), name + " card");
        Waits.resilient(title::click);
        return new PlanPreviewPage(driver, factory).waitUntilLoaded();
    }

    /**
     * Taps the card's "Generate". With no plan active this starts building
     * at once; with one active the app first asks "Replace your plan?".
     */
    @Step("Generate {name}")
    public void tapGenerate(String name) {
        int index = 0;
        String current = null;
        boolean afterPreview = false;
        for (String[] element : elementsInOrder()) {
            if (isText(element, "Plan Preview")) {
                afterPreview = true;
                continue;
            }
            if (afterPreview && "XCUIElementTypeStaticText".equals(element[0])) {
                current = element[1];
                afterPreview = false;
            }
            if ("XCUIElementTypeButton".equals(element[0]) && "Generate".equals(element[1])) {
                if (name.equals(current)) {
                    break;
                }
                index++;
            }
        }
        int target = index;
        scrollIntoView(Locators.staticText(name), name + " card");
        WebElement button = Waits.until(Waits.SHORT, "Generate on " + name, () -> {
            List<WebElement> buttons = driver.findElements(Locators.button("Generate"));
            return buttons.size() > target ? buttons.get(target) : null;
        });
        if (!Waits.resilientGet(button::isDisplayed)) {
            scrollOnce("down");
        }
        Waits.resilient(button::click);
    }

    /** "Replace your plan?" — shown when generating with another plan active. */
    public boolean showsReplaceDialog() {
        return isVisible(Locators.staticText("Replace your plan?"), Waits.SHORT);
    }

    public String replaceDialogMessage() {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf("Replace your plan?");
        return at >= 0 && at + 1 < texts.size() ? texts.get(at + 1) : null;
    }

    @Step("Replace dialog: {button}")
    public void answerReplaceDialog(String button) {
        tap(Locators.visibleButton(button), "replace-plan dialog");
        waitForGone(Locators.staticText("Replace your plan?"), Waits.SHORT);
    }

    private static boolean isText(String[] element, String label) {
        return "XCUIElementTypeStaticText".equals(element[0]) && label.equals(element[1]);
    }
}
