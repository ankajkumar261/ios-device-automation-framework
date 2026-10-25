package com.portfolio.ios.tests.health;

import com.portfolio.ios.base.AbstractAppTest;
import com.portfolio.ios.core.Artifacts;
import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.HealSuggester;
import com.portfolio.ios.core.Waits;
import com.portfolio.ios.pages.CoachPage;
import com.portfolio.ios.pages.FoodSearchPage;
import com.portfolio.ios.pages.HomePage;
import com.portfolio.ios.pages.Locators;
import com.portfolio.ios.pages.ScannerPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Locator health check — a quick, read-only pass over each main screen that
 * confirms the elements the tests rely on are still there, so a new build's
 * UI changes show up as one table per screen ("Coach · send button ·
 * MISSING") instead of dozens of failing tests.
 *
 * <p>Each element is reported as FOUND, HIDDEN (in the screen's tree but
 * reported not visible — e.g. the iOS 27 background-image issue) or MISSING
 * (not in the tree: the UI changed). Only MISSING fails a screen. The tables
 * are attached to Allure and printed; a failing screen also gets a screenshot.
 *
 * <p>Nothing is logged or changed in the app. Run alone with
 * {@code mvn test -Phealth}; about three minutes on the simulator.
 */
@Epic("Locator health")
@Feature("Key elements per screen")
public class LocatorHealthTest extends AbstractAppTest {

    private enum Status { FOUND, HIDDEN, MISSING }

    private record Landmark(String name, By locator) {
    }

    private static Landmark mark(String name, By locator) {
        return new Landmark(name, locator);
    }

    // ---------------------------------------------------------------- screens

    @Test(groups = {"health"}, priority = 0, description = "HEALTH_001 Home")
    @Description("Calorie card, header buttons, tab bar, and every tile and card further down")
    public void homeScreen() {
        HomePage home = home();
        List<Landmark> top = new ArrayList<>(List.of(
                mark("Remaining caption", Locators.homeRemainingCaption()),
                mark("Goal link", Locators.homeGoalButton()),
                mark("Calendar button", Locators.homeCalendarButton()),
                mark("Streak badge", Locators.homeStreakBadge()),
                mark("Create (+) button", Locators.homeCreateButtonCandidates())));
        for (String tab : Locators.MAIN_TABS) {
            top.add(mark("Tab " + tab, Locators.tabButton(tab)));
        }
        List<Landmark> below = new ArrayList<>();
        for (String tile : List.of("AI INSIGHT", "INTERMITTENT FASTING", "WEIGHT", "MOOD", "STEPS")) {
            below.add(mark("Tile " + tile, Locators.homeTile(tile)));
        }
        below.add(mark("Activities card", Locators.staticText("Activities")));
        below.add(mark("Water card", Locators.staticText("Water")));
        below.add(mark("Today's Meals", Locators.staticText("Today's Meals")));

        // Below-the-fold elements are scrolled to first; the tab bar is read at the top.
        check("Home", top, by -> null);
        check("Home (scrolled)", below, home::scrollIntoView);
    }

    @Test(groups = {"health"}, priority = 1, description = "HEALTH_002 Create menu")
    @Description("The '+' menu's entries")
    public void createMenu() {
        HomePage home = home();
        home.openCreateMenu();
        List<Landmark> items = new ArrayList<>();
        for (String item : List.of("Scan", "Search", "Voice", "Fridge Scan", "Workout", "Meal Plan")) {
            items.add(mark("Menu " + item, Locators.createMenuItem(item)));
        }
        try {
            check("Create menu", items, by -> null);
        } finally {
            home.closeCreateMenu();
        }
    }

    @Test(groups = {"health"}, priority = 2, description = "HEALTH_003 Food search")
    @Description("Add Food's title and search bar, and a result for 'banana'")
    public void foodSearch() {
        FoodSearchPage search = home().openFoodSearch();
        try {
            check("Food search", List.of(
                    mark("Title 'Add Food'", Locators.foodSearchTitle()),
                    mark("Search bar", Locators.foodSearchField())), by -> null);
            search.search("banana");
            check("Food search results", List.of(
                    mark("'Local' results section", Locators.staticText("Local")),
                    mark("A 'Banana' result", Locators.byLabelContaining("Banana"))), by -> null);
        } finally {
            search.close();
        }
    }

    @Test(groups = {"health"}, priority = 3, description = "HEALTH_004 AI Coach")
    @Description("Coach title, close, text field, send button and the prompt chips")
    public void coach() {
        CoachPage coach = home().openCoach();
        List<Landmark> marks = new ArrayList<>(List.of(
                mark("Title 'AI Coach'", Locators.staticText(CoachPage.TITLE)),
                mark("Close", Locators.button("Close")),
                mark("Text field", Locators.textField()),
                mark("Send button", Locators.buttonNamed("arrow.up.circle.fill"))));
        for (String chip : CoachPage.PROMPTS) {
            marks.add(mark("Chip '" + chip + "'", Locators.button(chip)));
        }
        try {
            check("AI Coach", marks, by -> null);
        } finally {
            coach.close();
        }
    }

    @Test(groups = {"health"}, priority = 4, description = "HEALTH_005 Scanner")
    @Description("Scanner title, back, the five mode tabs, and the no-camera cover on the simulator")
    public void scanner() {
        ScannerPage scanner = home().openScanner().answerCameraPromptIfAsked(true);
        List<Landmark> marks = new ArrayList<>(List.of(
                mark("Title 'Scanner'", Locators.staticText("Scanner")),
                mark("Back", Locators.buttonNamed("scanBack"))));
        for (String mode : ScannerPage.MODES) {
            marks.add(mark("Mode " + mode, Locators.button(mode)));
        }
        if (!Config.isReal()) {
            marks.add(mark("'Camera Not Available'", Locators.staticText(ScannerPage.CAMERA_NOT_AVAILABLE)));
            marks.add(mark("'Choose from Library'", Locators.button("Choose from Library")));
        }
        try {
            check("Scanner", marks, by -> null);
        } finally {
            if (Config.isReal()) {
                scanner.back();
            } else {
                scanner.closeOverlay();
            }
        }
    }

    @Test(groups = {"health"}, priority = 5, description = "HEALTH_006 Meals, Insights and Profile tabs")
    @Description("Each tab's landing card and the controls the tests use there")
    public void tabs() {
        HomePage home = home();
        List<String> missing = new ArrayList<>();
        home.openTab("Meals");
        missing.addAll(checkQuietly("Meals tab", List.of(mark("'Available Meal Plans'", Locators.mealsLanding()))));
        home.openTab("Insights");
        missing.addAll(checkQuietly("Insights tab", List.of(
                mark("'This Week vs Last Week'", Locators.insightsLanding()),
                mark("'Goals'", Locators.staticText("Goals")),
                mark("'Health Score'", Locators.staticText("Health Score")))));
        home.openTab("Profile");
        missing.addAll(checkQuietly("Profile tab", List.of(
                mark("'About You'", Locators.profileLanding()),
                mark("Settings gear", Locators.profileSettingsButton()),
                mark("Apple Health switch", Locators.appleHealthSwitch()))));
        home.openTab("Home");
        failIfMissing("Meals, Insights and Profile tabs", missing);
    }

    // ---------------------------------------------------------------- checking

    /**
     * Checks every landmark (after {@code bringIntoView}, when it does
     * something) and fails naming the missing ones.
     */
    private void check(String screen, List<Landmark> landmarks, Function<By, WebElement> bringIntoView) {
        failIfMissing(screen, checkQuietly(screen, landmarks, bringIntoView));
    }

    private List<String> checkQuietly(String screen, List<Landmark> landmarks) {
        return checkQuietly(screen, landmarks, by -> null);
    }

    /** Reports each landmark's status; returns the missing ones (screenshot taken if any). */
    private List<String> checkQuietly(String screen, List<Landmark> landmarks, Function<By, WebElement> bringIntoView) {
        StringBuilder table = new StringBuilder(String.format("%-30s %-8s %s%n", "element", "status", "locator"));
        List<String> missing = new ArrayList<>();
        String source = null;
        for (Landmark landmark : landmarks) {
            bringIntoView.apply(landmark.locator());
            Status status = status(landmark.locator());
            table.append(String.format("%-30s %-8s %s%n", landmark.name(), status, landmark.locator()));
            if (status == Status.MISSING) {
                // Heal suggestion: the closest element on this screen, never tapped.
                source = source != null ? source : driver.getPageSource();
                List<HealSuggester.Suggestion> closest = HealSuggester.suggest(landmark.locator(), source);
                String hint = closest.isEmpty() ? "nothing similar on screen" : "closest: " + closest.get(0);
                table.append(String.format("%-30s %-8s %s%n", "", "", hint));
                missing.add(landmark.name() + " [" + landmark.locator() + "] — " + hint);
            }
            if (status != Status.FOUND) {
                System.out.println("[iosautomation] locator health: " + screen + " · " + landmark.name() + " · " + status);
            }
        }
        Allure.addAttachment("locator-health " + screen, "text/plain", table.toString(), ".txt");
        if (!missing.isEmpty()) {
            Artifacts.capture(driver, "health-" + screen.replaceAll("[^A-Za-z]+", "-"));
        }
        return missing;
    }

    private static void failIfMissing(String screen, List<String> missing) {
        if (!missing.isEmpty()) {
            throw new AssertionError("Locator health: not found on " + screen + ": " + String.join("; ", missing));
        }
    }

    /** MISSING if not in the tree within a moment; HIDDEN if there but no match reports visible. */
    private Status status(By locator) {
        if (!Waits.untilQuietly(Waits.QUICK, () -> !driver.findElements(locator).isEmpty())) {
            return Status.MISSING;
        }
        boolean visible = Waits.untilQuietly(Duration.ofSeconds(1), () -> driver.findElements(locator).stream()
                .anyMatch(e -> "true".equals(Waits.resilientGet(() -> e.getAttribute("visible")))));
        return visible ? Status.FOUND : Status.HIDDEN;
    }
}
