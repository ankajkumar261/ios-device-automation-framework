package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Home tab: header, week strip, calorie card, macro cards, tiles and the
 * read-only sheets they open.
 *
 * <p>Everything here is computed on the device from local data, so values are
 * deterministic for a known profile. Two layout facts shape this class:
 * <ul>
 *   <li>The calorie number and the macro grams are unlabeled texts sitting next
 *       to their captions ("Remaining", "Protein"…). They are read from one
 *       page-source snapshot in document order rather than by XPath.</li>
 *   <li>The tab bar collapses to the active tab when Home is scrolled down and
 *       only comes back near the top, so tab taps scroll up first.</li>
 * </ul>
 */
public final class HomePage extends BasePage {

    /** Every greeting the header can show, by hour of day. */
    public static final List<String> GREETINGS =
            List.of("Good morning", "Good afternoon", "Good evening", "Hey there");

    /** Labels of the macro cards, top to bottom. */
    public static final List<String> MACROS = List.of("Protein", "Carbs", "Fat");

    /** Session locale is pinned to en_US, so dates render like "Oct 1" and "1, Thu". */
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_PILL = DateTimeFormatter.ofPattern("d, EEE", Locale.US);

    /** Half-height scroll steps; Home is about two screens tall. */
    private static final int MAX_SCROLLS = 10;
    private static final double SCROLL_STEP = 0.5;
    /** Buttons that close whatever is over Home, in the order they are preferred. */
    private static final List<String> DISMISS_LABELS = List.of("Discard", "Keep Fasting", "Not now", "Cancel",
            "close", "Close", "Done", "Back", "Go Down");

    public HomePage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    // ---------------------------------------------------------------- arriving

    /**
     * Waits until the calorie card is on screen.
     *
     * <p>Fails at once, not after a timeout, if the force-update cover is up:
     * it cannot be dismissed, and every Home check would otherwise time out
     * with a message that hides the real cause.
     */
    @Step("Wait for Home to load")
    public HomePage waitUntilLoaded() {
        Waits.until(Waits.MEDIUM, "Home calorie card", () -> {
            if (Waits.isPresent(driver, Locators.forceUpdateCover())) {
                throw new AssertionError("Blocked by the force-update screen ('Update Required'): "
                        + "the backend reports a latestVersion at or above this build's version, "
                        + "so Home is covered and cannot be tested");
            }
            // While a fast runs (or just ended) the top card can show its
            // fasting page, which moves "Remaining" off-screen to the side.
            return Waits.isPresent(driver, Locators.homeRemainingCaption())
                    || Waits.isPresent(driver, Locators.fastingCardTitle()) ? Boolean.TRUE : null;
        });
        return this;
    }

    /**
     * Puts the app on the Home tab, scrolled to the top, with no sheet open.
     * Every Home test starts here, so one failed test cannot strand the next.
     */
    @Step("Open Home")
    public HomePage open() {
        closeOpenSheets();
        if (!Waits.isPresent(driver, Locators.homeCalendarButton())) {
            openTab("Home");
        }
        scrollToTop();
        if (isPastDayBannerShowing()) {
            // A previous test left Home on a past day; every test starts on today.
            backToToday();
        }
        return waitUntilLoaded();
    }

    /**
     * Home again after a screen opened from low on Home closes — the fasting
     * hub or setup, reached through the tall "Upcoming fast" card: scrolled back
     * to the top, so the calorie card is on screen when it is checked.
     */
    public HomePage backAtTop() {
        scrollToTop();
        return waitUntilLoaded();
    }

    /** True when Home's header is on screen. */
    public boolean isShowing() {
        return Waits.isPresent(driver, Locators.homeCalendarButton());
    }

    /**
     * Closes any sheet left open, preferring the button that discards changes.
     * Home itself has no Cancel / Close / Done buttons, so a match is a sheet.
     */
    private void closeOpenSheets() {
        // Clean Home (the usual case): one query finds nothing to close.
        if (driver.findElements(Locators.anythingToDismiss(DISMISS_LABELS)).isEmpty()) {
            return;
        }
        for (int round = 0; round < 4; round++) {
            dismissContextMenuIfOpen();
            dismissActionSheetIfOpen();
            WebElement dismiss = null;
            // "Discard" answers a live workout's "End workout?" alert first and
            // "Keep Fasting" an end-fast alert; "Not now" a "Replace your plan?" prompt;
            // "close" ends an active search in Add Food (its Cancel is hidden then);
            // "Back" leaves a logged food's full-screen detail; "Go Down" closes
            // an idle live workout.
            for (String label : DISMISS_LABELS) {
                if (Waits.isPresent(driver, Locators.button(label))) {
                    dismiss = driver.findElement(Locators.button(label));
                    break;
                }
            }
            if (dismiss == null) {
                return;
            }
            Waits.resilient(dismiss::click);
            Waits.untilQuietly(Waits.QUICK, () -> !Waits.isPresent(driver, Locators.button("Done"))
                    && !Waits.isPresent(driver, Locators.button("Cancel"))
                    && !Waits.isPresent(driver, Locators.button("close"))
                    && !Waits.isPresent(driver, Locators.button("Close"))
                    && !Waits.isPresent(driver, Locators.button("Back")));
        }
    }

    /**
     * Closes an open long-press menu, which hides every other control. iOS
     * gives its dimmed background no element, so this taps the screen
     * half-way above the lifted row — a point derived from the row itself.
     */
    public void dismissContextMenuIfOpen() {
        List<WebElement> previews = driver.findElements(Locators.contextMenuPreview());
        if (previews.isEmpty() || !Waits.resilientGet(previews.get(0)::isDisplayed)) {
            return;
        }
        org.openqa.selenium.Rectangle row = Waits.resilientGet(previews.get(0)::getRect);
        org.openqa.selenium.Dimension window = driver.manage().window().getSize();
        com.portfolio.ios.core.Gestures.tapAt(driver, window.getWidth() / 2, Math.max(1, row.getY() / 2));
        Waits.untilQuietly(Waits.SHORT, () -> !Waits.isPresent(driver, Locators.contextMenuPreview()));
    }

    /**
     * Closes an open popover-style action sheet (a confirmation dialog whose
     * Cancel iOS hides). Its dismiss region covers the whole screen, with the
     * sheet on top of its centre, so this taps the region half-way above the
     * sheet — a point derived from both elements.
     *
     * @return true if a sheet was open and is now gone
     */
    public boolean dismissActionSheetIfOpen() {
        List<WebElement> sheets = driver.findElements(Locators.byType("XCUIElementTypeSheet"));
        List<WebElement> regions = driver.findElements(Locators.popoverDismissRegion());
        if (sheets.isEmpty() || regions.isEmpty() || !Waits.resilientGet(sheets.get(0)::isDisplayed)) {
            return false;
        }
        org.openqa.selenium.Rectangle sheet = Waits.resilientGet(sheets.get(0)::getRect);
        org.openqa.selenium.Rectangle region = Waits.resilientGet(regions.get(0)::getRect);
        com.portfolio.ios.core.Gestures.tapAt(driver, region.getX() + region.getWidth() / 2,
                region.getY() + Math.max(1, (sheet.getY() - region.getY()) / 2));
        return Waits.untilQuietly(Waits.SHORT,
                () -> driver.findElements(Locators.byType("XCUIElementTypeSheet")).isEmpty());
    }

    // ---------------------------------------------------------------- tabs

    /**
     * Taps a tab. Scrolls the current screen up first when the bar has
     * collapsed to the active tab only.
     */
    @Step("Open the {label} tab")
    public void openTab(String label) {
        By tab = Locators.tabButton(label);
        for (int i = 0; i < MAX_SCROLLS && !Waits.isPresent(driver, tab); i++) {
            scrollMain("up");
        }
        tap(tab, "tab bar");
    }

    public boolean isTabVisible(String label) {
        return Waits.isPresent(driver, Locators.tabButton(label));
    }

    /**
     * Waits for a tab's landing marker. Some landings fill in after their data
     * loads (Insights), so this waits rather than checking once.
     */
    public boolean showsWithin(By landingMarker) {
        return isVisible(landingMarker, Waits.MEDIUM);
    }

    // ---------------------------------------------------------------- header

    /** The device's own date, which is what the app renders "Today" from. */
    public LocalDate deviceToday() {
        return LocalDate.parse(driver.getDeviceTime("YYYY-MM-DD"));
    }

    /** The header title the app shows for {@code date} when it is today. */
    public static String todayTitle(LocalDate date) {
        return "Today, " + date.format(HEADER_DATE);
    }

    public String headerTitle() {
        return textOf(Locators.homeHeaderTitle(), Waits.SHORT);
    }

    public String greeting() {
        return textOf(Locators.homeGreeting(GREETINGS), Waits.SHORT);
    }

    public String streakBadgeLabel() {
        return Waits.resilientGet(() -> visible(Locators.homeStreakBadge(), Waits.SHORT)
                .getAttribute("label"));
    }

    // ---------------------------------------------------------------- calorie card

    /** Daily goal from the "Goal 2234 kcal" button. */
    public int goalKcal() {
        String label = Waits.resilientGet(() -> visible(Locators.homeGoalButton(), Waits.SHORT)
                .getAttribute("label"));
        return digits(label);
    }

    /**
     * The big number under "Remaining". It has no label of its own, so it is
     * read as the text just before its unit, "kcal left" (or "kcal over"). Not
     * as the text after the caption: while a fast runs, the top card's two pages
     * share rows in the tree (app c3270fe), so "Remaining" is followed by the
     * fasting page's "Started" and the number comes a few texts later.
     */
    public int remainingKcal() {
        List<String> texts = staticTexts();
        for (int i = 1; i < texts.size(); i++) {
            if (List.of("kcal left", "kcal over").contains(texts.get(i))
                    && texts.get(i - 1).matches("[−-]?[0-9][0-9,]*")) {
                return digits(texts.get(i - 1));
            }
        }
        throw new AssertionError("No calorie number before 'kcal left' on Home; texts on screen: " + texts);
    }

    /** Eaten and target grams of one macro card. */
    public record Macro(int eatenG, int targetG) {
    }

    /**
     * Reads the three macro cards. The tree lists the captions first, then
     * "0g", "/167g" pairs in the same order (Protein, Carbs, Fat).
     */
    public Map<String, Macro> macros() {
        List<String> texts = staticTexts();
        int start = texts.indexOf("Protein");
        if (start < 0) {
            throw new AssertionError("Macro cards not found; texts on screen: " + texts);
        }
        List<Integer> grams = new ArrayList<>();
        for (int i = start; i < texts.size() && grams.size() < 6; i++) {
            if (texts.get(i).matches("/?\\d+g")) {
                grams.add(digits(texts.get(i)));
            }
        }
        if (grams.size() < 6) {
            throw new AssertionError("Expected 3 eaten/target pairs after 'Protein', found " + grams);
        }
        Map<String, Macro> result = new LinkedHashMap<>();
        for (int i = 0; i < MACROS.size(); i++) {
            result.put(MACROS.get(i), new Macro(grams.get(2 * i), grams.get(2 * i + 1)));
        }
        return result;
    }

    // ---------------------------------------------------------------- tiles and sections

    /**
     * Scrolls Home until {@code by} is on screen and returns it, or null if it
     * never appears. Tries downward from the current position, then once more
     * from the top, so callers need not check sections in screen order.
     */
    public WebElement scrollIntoView(By by) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i <= MAX_SCROLLS; i++) {
                if (Waits.isPresent(driver, by)) {
                    return clearOfTabBar(by);
                }
                if (!scrollMain("down")) {
                    break;
                }
            }
            scrollToTop();
        }
        return null;
    }

    /**
     * The floating tab bar sits over the bottom of Home, and a tap on an
     * element whose middle is under it goes to a tab instead (observed on the
     * iPhone 15 Pro: the fasting tile's tap opened Insights). Scrolls on until
     * the middle clears the bar, or Home cannot scroll further.
     */
    private WebElement clearOfTabBar(By by) {
        WebElement element = driver.findElement(by);
        for (int i = 0; i < 3; i++) {
            org.openqa.selenium.Rectangle frame = Waits.resilientGet(element::getRect);
            if (frame.getY() + frame.getHeight() / 2 < tabBarTop() - 8 || !scrollMain("down")) {
                return element;
            }
            element = driver.findElement(by);
        }
        return element;
    }

    /** Top of the floating tab bar (its Home tab), or a huge value when it is not shown. */
    private int tabBarTop() {
        List<WebElement> tabs = driver.findElements(Locators.tab("Home"));
        return tabs.isEmpty() ? Integer.MAX_VALUE : Waits.resilientGet(tabs.get(0)::getRect).getY();
    }

    /** Label of a tile found by scrolling, or null if it is not on Home. */
    public String tileLabel(String titlePrefix) {
        WebElement tile = scrollIntoView(Locators.homeTile(titlePrefix));
        return tile == null ? null : Waits.resilientGet(() -> tile.getAttribute("label"));
    }

    /**
     * Scrolls Home back to the top — and does nothing if it is already there.
     *
     * <p>Scrolling up while already at the top makes iOS rubber-band the
     * content down and back, so the position is measured first. The header is
     * pinned above the scroll view and says nothing about position; the week
     * strip is the first thing inside the scroll view, so when it sits at or
     * below the scroll view's top edge, Home is at the top. When it does not,
     * scroll up by about the distance it has moved, not by a whole page.
     */
    @Step("Scroll Home to the top")
    public void scrollToTop() {
        for (int i = 0; i < MAX_SCROLLS; i++) {
            List<WebElement> views = driver.findElements(Locators.scrollView());
            if (views.size() < 2) {
                return;
            }
            org.openqa.selenium.Rectangle main = Waits.resilientGet(views.get(0)::getRect);
            org.openqa.selenium.Rectangle strip = Waits.resilientGet(views.get(1)::getRect);
            int scrolledBy = main.getY() - strip.getY();
            if (scrolledBy <= 0) {
                return;
            }
            double distance = Math.min(1.0, (scrolledBy + 20.0) / main.getHeight());
            if (!scrollMain("up", distance)) {
                return;
            }
        }
    }

    /** One half-page scroll of the main scroll view; false if there is none. */
    private boolean scrollMain(String direction) {
        return scrollMain(direction, SCROLL_STEP);
    }

    private boolean scrollMain(String direction, double distance) {
        for (WebElement view : driver.findElements(Locators.scrollView())) {
            try {
                if (view.isDisplayed()) {
                    Gestures.scroll(driver, view, direction, distance);
                    return true;
                }
            } catch (RuntimeException atEdge) {
                // Already at the end in this direction, or the view was rebuilt.
                return false;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- create menu and logging

    /**
     * Opens the "+" create menu. The create button shares name and label with
     * the water card's "+" buttons, so the widest match is taken (62pt vs 32pt).
     * No-op if the menu is already open.
     */
    @Step("Open the create menu")
    public void openCreateMenu() {
        if (Waits.isPresent(driver, Locators.createMenuItem("Search"))) {
            return;
        }
        Waits.resilient(createButton()::click);
        visible(Locators.createMenuItem("Search"), Waits.SHORT, "create menu 'Search' item");
    }

    /** Closes the "+" create menu (the same button toggles it); no-op if it is closed. */
    @Step("Close the create menu")
    public void closeCreateMenu() {
        if (!Waits.isPresent(driver, Locators.createMenuItem("Search"))) {
            return;
        }
        Waits.resilient(createButton()::click);
        waitForGone(Locators.createMenuItem("Search"), Waits.SHORT);
    }

    private WebElement createButton() {
        return Waits.until(Waits.SHORT, "the round '+' create button", () -> {
            WebElement widest = null;
            int widestWidth = 0;
            for (WebElement candidate : driver.findElements(Locators.homeCreateButtonCandidates())) {
                int width = candidate.getRect().getWidth();
                if (width > widestWidth) {
                    widest = candidate;
                    widestWidth = width;
                }
            }
            return widest;
        });
    }

    /** "+" → "Scan": opens the Scanner (the camera prompt, if iOS asks, is left to the caller). */
    @Step("Open the Scanner")
    public ScannerPage openScanner() {
        openCreateMenu();
        tap(Locators.createMenuItem("Scan"), "create menu");
        return new ScannerPage(driver, factory).waitUntilLoaded();
    }

    /** "+" → "Voice": opens Voice Input. */
    @Step("Open Voice Input")
    public VoiceInputPage openVoiceInput() {
        openCreateMenu();
        tap(Locators.createMenuItem("Voice"), "create menu");
        return new VoiceInputPage(driver, factory).waitUntilLoaded();
    }

    /** "+" → "Search": opens the Add Food screen. */
    @Step("Open food search")
    public FoodSearchPage openFoodSearch() {
        openCreateMenu();
        tap(Locators.createMenuItem("Search"), "create menu");
        return new FoodSearchPage(driver, factory).waitUntilLoaded();
    }

    /**
     * Waits out what a log can bring up over Home: the streak celebration
     * (first log of the day; auto-dismisses after ~3s) and, as a fallback,
     * StoreKit's review prompt. Then Home is readable again.
     */
    @Step("Let Home settle after a log")
    public HomePage settleAfterLog() {
        // The prompt is switched off by -disableReviewPrompt: check, don't wait for it.
        if (Waits.isPresent(driver, Locators.reviewPromptNotNow())) {
            tap(Locators.reviewPromptNotNow(), "review prompt");
        }
        // The streak celebration closes on a tap (StreakCelebrationOverlay.swift), so tap it
        // rather than wait it out: waiting took the full 8 s in 10 of 23 logs on the iPhone.
        if (Waits.isPresent(driver, Locators.streakCelebration())) {
            try {
                driver.findElement(Locators.streakCelebration()).click();
            } catch (org.openqa.selenium.NoSuchElementException
                     | org.openqa.selenium.StaleElementReferenceException alreadyGone) {
                // It closed on its own in the meantime.
            }
            Waits.untilQuietly(Waits.SHORT, () -> !Waits.isPresent(driver, Locators.streakCelebration()));
        }
        scrollToTop();
        return waitUntilLoaded();
    }

    /**
     * True when the timeline's meal card {@code mealHeader} ("BREAKFAST" …)
     * lists a row for {@code foodName}. Reads the tree in order: a card's rows
     * follow its header until the next card header.
     */
    public boolean mealCardContains(String mealHeader, String foodName) {
        List<String[]> elements = elementsInOrder();
        String rowMarker = ", " + foodName + ", ";
        boolean inCard = false;
        for (String[] element : elements) {
            String type = element[0];
            String label = element[1];
            if ("XCUIElementTypeStaticText".equals(type) && Locators.MEAL_CARD_HEADERS.contains(label)) {
                inCard = label.equals(mealHeader);
                continue;
            }
            if (inCard && "XCUIElementTypeButton".equals(type)) {
                // Food rows start with an emoji ("🍌, Banana, …"); rows logged
                // from a meal plan start with the name ("Frittata, 559, …").
                if (label.contains(rowMarker) || label.startsWith(foodName + ", ")) {
                    return true;
                }
                if (Locators.MAIN_TABS.contains(label)) {
                    return false;   // walked past the timeline into the tab bar
                }
            }
        }
        return false;
    }

    /** Scrolls to the WEIGHT tile (today) and opens the weight sheet. */
    @Step("Open the weight sheet")
    public WeightPage openWeight() {
        WebElement tile = scrollIntoView(Locators.homeTile("WEIGHT"));
        if (tile == null) {
            throw new AssertionError("WEIGHT tile is not on Home");
        }
        Waits.resilient(tile::click);
        return new WeightPage(driver, factory).waitUntilLoaded();
    }

    /** Scrolls to the MOOD tile (today) and opens the mood sheet. */
    @Step("Open the mood sheet")
    public MoodPage openMood() {
        WebElement tile = scrollIntoView(Locators.homeTile("MOOD"));
        if (tile == null) {
            throw new AssertionError("MOOD tile is not on Home");
        }
        Waits.resilient(tile::click);
        return new MoodPage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- fasting

    /**
     * Taps Home's fasting entry — the "INTERMITTENT FASTING" invite or, with a
     * plan, the "Upcoming fast" card; what opens depends on the fasting state.
     */
    private void tapFastingTile() {
        WebElement tile = scrollIntoView(Locators.homeFastingEntry());
        if (tile == null) {
            throw new AssertionError("Fasting entry ('INTERMITTENT FASTING' or 'Upcoming fast') is not on Home");
        }
        Waits.resilient(tile::click);
    }

    /** First use (no plan, no fast ever): the tile opens the setup flow. */
    @Step("Open fasting setup from Home")
    public FastingSetupPage openFastingSetup() {
        tapFastingTile();
        return new FastingSetupPage(driver, factory);
    }

    /** With a plan or any fast: the tile opens the hub. */
    @Step("Open the fasting hub from Home")
    public FastingHubPage openFastingHub() {
        tapFastingTile();
        return new FastingHubPage(driver, factory).waitUntilLoaded();
    }

    /**
     * Opens fasting and reports which screen came up: the hub, or setup
     * (education or protocol picker). Used by resets that start from any state.
     */
    public BasePage openFasting() {
        tapFastingTile();
        return Waits.until(Waits.MEDIUM, "fasting hub or setup", () -> {
            if (Waits.isPresent(driver, Locators.staticText("Recent Fasts"))) {
                return new FastingHubPage(driver, factory);
            }
            if (Waits.isPresent(driver, Locators.staticText("Select Fasting Protocol"))
                    || Waits.isPresent(driver, Locators.staticText("What is intermittent fasting?"))) {
                return new FastingSetupPage(driver, factory);
            }
            return null;
        });
    }

    public FastingCardPage fastingCard() {
        return new FastingCardPage(driver, factory);
    }

    // ---------------------------------------------------------------- activities

    /** Activities card "Add": opens the "Log activities" sheet. */
    @Step("Open Log activities from Home")
    public ActivityCatalogSheet openActivityCatalog() {
        WebElement add = scrollIntoView(Locators.homeActivitiesAddButton());
        if (add == null) {
            throw new AssertionError("Activities 'Add' is not on Home");
        }
        Waits.resilient(add::click);
        return new ActivityCatalogSheet(driver, factory).waitUntilLoaded();
    }

    /** Activities card "View all activities": opens Log Exercise. */
    @Step("Open Log Exercise from Home")
    public LogExercisePage openLogExercise() {
        WebElement viewAll = scrollIntoView(Locators.button("View all activities"));
        if (viewAll == null) {
            throw new AssertionError("'View all activities' is not on Home (nothing logged today?)");
        }
        Waits.resilient(viewAll::click);
        return new LogExercisePage(driver, factory).waitUntilLoaded();
    }

    /** "+" → "Workout": the live workout timer (today only). */
    @Step("Open the live workout timer from Home")
    public LiveWorkoutPage openLiveWorkout() {
        openCreateMenu();
        tap(Locators.createMenuItem("Workout"), "create menu");
        return new LiveWorkoutPage(driver, factory).waitUntilLoaded();
    }

    /**
     * The Activities card's latest entry as {name, "30 min · 343 kcal"}, or
     * null while the card is header-only (nothing logged today). Read in tree
     * order: the entry's two texts sit between "Activities" and
     * "View all activities".
     */
    public List<String> latestActivity() {
        if (scrollIntoView(Locators.homeActivitiesAddButton()) == null) {
            throw new AssertionError("Activities card is not on Home");
        }
        List<String> texts = new java.util.ArrayList<>();
        boolean inCard = false;
        for (String[] element : elementsInOrder()) {
            String type = element[0];
            String label = element[1];
            if ("XCUIElementTypeStaticText".equals(type) && "Activities".equals(label)) {
                inCard = true;
                continue;
            }
            if (!inCard) {
                continue;
            }
            if ("XCUIElementTypeButton".equals(type) && "View all activities".equals(label)) {
                return texts.size() == 2 ? texts : null;
            }
            if ("XCUIElementTypeButton".equals(type) && !"Add".equals(label)) {
                return null;   // walked past a header-only card into the next card
            }
            if ("XCUIElementTypeStaticText".equals(type) && !"Add".equals(label)) {
                texts.add(label);
            }
        }
        return null;
    }

    /** True when today's timeline has a row for {@code foodName}. */
    public boolean hasLoggedFood(String foodName) {
        return !driver.findElements(Locators.foodRow(foodName)).isEmpty();
    }

    /** Full accessibility label of the timeline row for {@code foodName}, or null. */
    public String loggedRowLabel(String foodName) {
        List<WebElement> rows = driver.findElements(Locators.foodRow(foodName));
        return rows.isEmpty() ? null : Waits.resilientGet(() -> rows.get(0).getAttribute("label"));
    }

    /** Scrolls to the timeline row for {@code foodName} and opens its detail. */
    @Step("Open logged food {foodName}")
    public FoodDetailPage openLoggedFood(String foodName) {
        return openRow(Locators.foodRow(foodName), "timeline row " + foodName);
    }

    /**
     * Calorie total on a meal card ("BREAKFAST" … "105 kcal"), or -1 when the
     * card is not shown — the app removes a card whose last entry goes.
     */
    public int mealCardKcal(String mealHeader) {
        List<String> texts = staticTexts();
        int header = texts.indexOf(mealHeader);
        if (header < 0) {
            return -1;
        }
        for (int i = header + 1; i < texts.size(); i++) {
            if (texts.get(i).matches("\\d+ kcal")) {
                return digits(texts.get(i));
            }
        }
        return -1;
    }

    /** Full labels of every row in today's timeline, top to bottom. */
    public List<String> loggedRowLabels() {
        List<String> rows = new ArrayList<>();
        boolean inCard = false;
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])
                    && Locators.MEAL_CARD_HEADERS.contains(element[1])) {
                inCard = true;
            } else if (inCard && "XCUIElementTypeButton".equals(element[0])) {
                if (Locators.MAIN_TABS.contains(element[1])) {
                    break;   // walked past the timeline into the tab bar
                }
                rows.add(element[1]);
            }
        }
        return rows;
    }

    /**
     * Deletes every entry in today's timeline through the UI, one at a time:
     * open → Trash → Delete. Bounded so a delete that does not take effect
     * fails instead of looping.
     */
    @Step("Delete every logged food")
    public HomePage deleteEveryLoggedFood() {
        for (int i = 0; i < 30; i++) {
            List<String> rows = loggedRowLabels();
            if (rows.isEmpty()) {
                return this;
            }
            openRow(Locators.byTypeAndLabel("XCUIElementTypeButton", rows.get(0)), "timeline row")
                    .tapTrash()
                    .confirmDelete();
        }
        throw new AssertionError("Entries remain after 30 deletes: " + loggedRowLabels());
    }

    private FoodDetailPage openRow(By row, String what) {
        WebElement element = scrollIntoView(row);
        if (element == null) {
            throw new AssertionError(what + " is not on Home");
        }
        Waits.resilient(element::click);
        return new FoodDetailPage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- calorie detail

    @Step("Open Calorie Detail from the goal button")
    public void openCalorieDetail() {
        tap(Locators.homeGoalButton(), "Goal button on the calorie card");
        visible(Locators.staticText("Calories"), Waits.MEDIUM, "Calorie Detail title 'Calories'");
    }

    /** True while Calorie Detail shows its Day/Week/Month/Year picker and today's caption. */
    public boolean isCalorieDetailShowing() {
        for (String range : List.of("Day", "Week", "Month", "Year")) {
            if (!Waits.isPresent(driver, Locators.button(range))) {
                return false;
            }
        }
        return Waits.isPresent(driver, Locators.staticText("TODAY"));
    }

    /** Switches the range and waits for the title "Calories · Week" (etc.). */
    @Step("Show the {range} range in Calorie Detail")
    public void selectCalorieDetailRange(String range) {
        tap(Locators.button(range), "Calorie Detail range picker");
        visible(Locators.staticText("Calories · " + range), Waits.SHORT,
                "Calorie Detail title for the " + range + " range");
    }

    @Step("Close Calorie Detail")
    public void closeCalorieDetail() {
        tap(Locators.button("Done"), "Calorie Detail Done");
        waitUntilLoaded();
    }

    // ---------------------------------------------------------------- goal info sheet

    @Step("Open 'How your goal is set'")
    public void openGoalInfo() {
        tap(Locators.homeGoalInfoButton(), "goal info button");
        visible(Locators.staticText("Daily target"), Waits.MEDIUM, "goal info sheet 'Daily target' row");
    }

    public boolean isGoalInfoShowing() {
        return Waits.isPresent(driver, Locators.staticText("Daily target"));
    }

    @Step("Close 'How your goal is set'")
    public void closeGoalInfo() {
        tap(Locators.button("Done"), "goal info Done");
        waitForGone(Locators.staticText("Daily target"), Waits.SHORT);
    }

    // ---------------------------------------------------------------- streak sheet

    @Step("Open the streak info sheet")
    public void openStreakInfo() {
        tap(Locators.homeStreakBadge(), "streak badge");
        visible(Locators.staticText("Cal Care Streaks"), Waits.MEDIUM, "streak sheet title");
    }

    public boolean isStreakInfoShowing() {
        return Waits.isPresent(driver, Locators.staticText("Cal Care Streaks"));
    }

    @Step("Close the streak info sheet")
    public void closeStreakInfo() {
        tap(Locators.button("Close"), "streak sheet Close");
        waitForGone(Locators.staticText("Cal Care Streaks"), Waits.SHORT);
    }

    // ---------------------------------------------------------------- AI coach

    /** The AI INSIGHT banner ("· tap to ask Coach") opens the AI Coach. */
    @Step("Open the AI Coach from the AI INSIGHT banner")
    public CoachPage openCoach() {
        WebElement banner = scrollIntoView(Locators.homeTile("AI INSIGHT"), "AI INSIGHT banner");
        Waits.resilient(banner::click);
        return new CoachPage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- calendar sheet

    @Step("Open the calendar")
    public void openCalendar() {
        tap(Locators.homeCalendarButton(), "calendar button");
        visible(Locators.staticText("Pick a Day"), Waits.MEDIUM, "calendar sheet title");
    }

    public boolean isCalendarShowing() {
        return Waits.isPresent(driver, Locators.staticText("Pick a Day"));
    }

    @Step("Cancel the calendar")
    public void cancelCalendar() {
        tap(Locators.button("Cancel"), "calendar Cancel");
        waitForGone(Locators.staticText("Pick a Day"), Waits.SHORT);
    }

    // ---------------------------------------------------------------- past days
    //
    // Formats from HomeView.swift (en_US): header "Yesterday, Oct 4" or plain
    // "Oct 3"; banner "EDITING PAST DAY" + "Sun, Oct 4"; meals heading
    // "Yesterday's Meals" / "Oct 3 Meals"; previous-date prompt subtitle
    // "Oct 4, 2026"; calendar footer "You can view data from Oct 2 onward.".

    private static final DateTimeFormatter BANNER_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter PROMPT_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /** Header title the app shows for {@code date}, given today. */
    public static String titleFor(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return todayTitle(date);
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday, " + date.format(HEADER_DATE);
        }
        return date.format(HEADER_DATE);
    }

    /** True when the header shows exactly {@code title}. */
    public boolean showsTitle(String title) {
        return Waits.untilQuietly(Waits.SHORT, () -> Waits.isPresent(driver, Locators.staticText(title)));
    }

    public boolean isPastDayBannerShowing() {
        return Waits.isPresent(driver, Locators.staticText("EDITING PAST DAY"));
    }

    public boolean bannerShowsDate(LocalDate date) {
        return Waits.isPresent(driver, Locators.staticText(date.format(BANNER_DATE)));
    }

    /**
     * The banner's "Today" button: back to today. Matched exactly — a
     * case-insensitive "contains" also hits the streak badge, labelled
     * "0 of 4 streaks active today", which comes first in the tree.
     */
    @Step("Back to today")
    public HomePage backToToday() {
        tap(Locators.button("Today"), "past-day banner");
        Waits.untilTrue(Waits.SHORT, "past-day banner to close", () -> !isPastDayBannerShowing());
        return this;
    }

    /** True when the timeline's heading reads {@code heading} ("Yesterday's Meals"). */
    public boolean showsMealsHeading(String heading) {
        return scrollIntoView(Locators.staticText(heading)) != null;
    }

    /** True when a Home tile (whole-card Button) exists for the shown day. */
    public boolean hasTile(String titlePrefix) {
        return !driver.findElements(Locators.homeTile(titlePrefix)).isEmpty();
    }

    /** True when the open "+" menu offers {@code item}. */
    public boolean createMenuOffers(String item) {
        return Waits.isPresent(driver, Locators.createMenuItem(item));
    }

    /** Taps a menu item on a past day; the app asks "Log for a previous date?" first. */
    @Step("Choose {item} on a past day")
    public HomePage chooseCreateItemOnPastDay(String item) {
        tap(Locators.createMenuItem(item), "create menu");
        visible(Locators.staticText("Log for a previous date?"), Waits.SHORT, "previous-date prompt");
        return this;
    }

    public boolean previousDatePromptShowsDate(LocalDate date) {
        return Waits.isPresent(driver, Locators.staticText(date.format(PROMPT_DATE)));
    }

    /**
     * Answers the previous-date prompt: "Yes" logs to the shown past day,
     * "Log for Today" switches to today first. Either way search opens.
     */
    @Step("Answer the previous-date prompt: {answer}")
    public FoodSearchPage answerPreviousDatePrompt(String answer) {
        tap(Locators.button(answer), "previous-date prompt");
        return new FoodSearchPage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- calendar days

    /** The footer naming the first selectable day: "You can view data from Oct 2 onward.". */
    public boolean calendarFooterNames(LocalDate firstDay) {
        // In the tree rather than on screen: the footer sits in a scroll area
        // that can be below the fold at the sheet's medium height.
        return !driver.findElements(Locators.staticText(
                "You can view data from " + firstDay.format(HEADER_DATE) + " onward.")).isEmpty();
    }

    /** A day cell of the calendar grid; cells are Buttons labelled with the day number only. */
    private By calendarDay(int dayOfMonth) {
        return Locators.button(String.valueOf(dayOfMonth));
    }

    @Step("Pick day {dayOfMonth} in the calendar")
    public HomePage pickCalendarDay(int dayOfMonth) {
        tap(calendarDay(dayOfMonth), "calendar grid");
        return this;
    }

    /** Enabled state of a day cell; null if the month on screen has no such cell. */
    public Boolean isCalendarDayEnabled(int dayOfMonth) {
        List<WebElement> cells = driver.findElements(calendarDay(dayOfMonth));
        return cells.isEmpty() ? null : Waits.resilientGet(cells.get(0)::isEnabled);
    }

    @Step("Confirm the calendar")
    public HomePage confirmCalendar() {
        tap(Locators.button("Done"), "calendar Done");
        waitForGone(Locators.staticText("Pick a Day"), Waits.SHORT);
        return this;
    }

    // ---------------------------------------------------------------- week strip

    /** The accessibility label the week strip gives {@code date}, e.g. "2, Fri". */
    public static String dayPillLabel(LocalDate date) {
        return date.format(DAY_PILL);
    }

    /** XCUITest type of the pill for {@code date}, or null if the strip has none. */
    public String dayPillType(LocalDate date) {
        By pill = Locators.homeDayPill(dayPillLabel(date));
        if (driver.findElements(pill).isEmpty()) {
            return null;
        }
        return Waits.resilientGet(() -> driver.findElement(pill).getAttribute("type"));
    }

    @Step("Tap the week-strip day {date}")
    public void tapDayPill(LocalDate date) {
        WebElement pill = Waits.visible(driver, Locators.homeDayPill(dayPillLabel(date)), Waits.SHORT,
                "week-strip pill " + dayPillLabel(date));
        Waits.resilient(pill::click);
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Labels of every StaticText in document order, from one page-source read.
     *
     * <p>One request instead of one per element, and the order is what places
     * an unlabeled number after its caption.
     */
    private List<String> staticTexts() {
        List<String> labels = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                labels.add(element[1]);
            }
        }
        return labels;
    }

}
