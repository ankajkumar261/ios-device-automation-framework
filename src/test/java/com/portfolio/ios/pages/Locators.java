package com.portfolio.ios.pages;

import com.portfolio.ios.core.IosBy;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

import java.util.List;

/**
 * Every selector in the suite.
 *
 * <p><b>Constraint that shapes this file.</b> The app declares no
 * {@code accessibilityIdentifier} anywhere (verified across all sources), so
 * {@link AppiumBy#accessibilityId(String)} resolves to the element's <i>visible
 * label</i> on XCUITest. Those labels are localised across 29 languages, which
 * is why sessions pin {@code en}/{@code en_US}.
 *
 * <p>When the app gains accessibility identifiers, this is the only file that
 * needs to change — page objects never build a {@code By} inline.
 *
 * <p>Labels come from {@code en.lproj/Localizable.strings} and are cross-checked
 * against a live accessibility-tree capture of the welcome step.
 *
 * <p><b>A trap worth knowing about.</b> WebDriverAgent reports a
 * <i>malformed</i> predicate as {@code NoSuchElementException}, not as
 * {@code InvalidSelectorException} — verified against WDA 16.1.6. A typo in a
 * predicate therefore looks exactly like an element that is genuinely absent,
 * and surfaces as a timeout far from its cause. When a locator here suddenly
 * stops matching, check the expression parses before assuming the app moved.
 */
public final class Locators {

    private Locators() {
    }

    // ---------------------------------------------------------------- predicate quoting

    /**
     * Renders {@code value} as an NSPredicate string literal.
     *
     * <p>Necessary rather than defensive: {@code iOSNsPredicateString} takes a
     * finished predicate with no varargs binding, and several real labels contain
     * characters that break an unquoted interpolation —
     * {@code "TAP & HOLD TO CONTINUE"}, {@code "I'm Ready!"}, {@code 5'11"}.
     */
    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    // ---------------------------------------------------------------- shared CTAs

    /**
     * Verified against a live tree capture:
     * {@code XCUIElementTypeButton name="Continue" label="Continue"}.
     */
    public static By continueButton() {
        return AppiumBy.accessibilityId("Continue");
    }

    /**
     * The back chevron is a bare {@code Image(systemName: "arrow.left")} with no
     * accessibility label, so it cannot be matched by name. The top bar renders
     * before step content, which makes the first button in document order the
     * back control.
     *
     * <p>Only valid from step 3 onward — the button is omitted from the layout
     * entirely on steps 0-2 and step 16, so there this locator resolves to the
     * step's primary CTA instead. Callers must guard on step index.
     */
    public static By backButton() {
        return By.xpath("//XCUIElementTypeButton[1]");
    }

    /** Exact visible-label match — option cards, diet names, yes/no rows. */
    public static By byExactLabel(String label) {
        return IosBy.predicate("label == " + quote(label));
    }

    /**
     * Step heading or caption on screen.
     *
     * <p>Same mechanism as {@link #byLabelContaining(String)}; named separately
     * because "is this screen showing?" and "is this control present?" are
     * different intents that happen to share a selector strategy, and tests read
     * better when the distinction is visible at the call site.
     */
    public static By headingContaining(String fragment) {
        return IosBy.predicate("label CONTAINS[c] " + quote(fragment));
    }

    /**
     * Substring label match, for copy assembled from concatenated localisation
     * fragments ("Almost " + "there...") whose runtime label is never spelled out
     * in full in the source.
     */
    public static By byLabelContaining(String fragment) {
        return IosBy.predicate("label CONTAINS[c] " + quote(fragment));
    }

    /** Any element of a given XCUITest type whose label matches exactly. */
    public static By byTypeAndLabel(String type, String label) {
        return IosBy.predicate("type == " + quote(type) + " AND label == " + quote(label));
    }

    /**
     * Element of a given type whose label contains {@code fragment}.
     *
     * <p>The type constraint is what separates a step's CTA from a neighbouring
     * step's heading that happens to open with the same words — see
     * {@link com.portfolio.ios.core.OnboardingStep}'s {@code markerType}.
     */
    public static By byTypeAndLabelContaining(String type, String fragment) {
        return IosBy.predicate(
                "type == " + quote(type) + " AND label CONTAINS[c] " + quote(fragment));
    }

    /** Visible element of a given XCUITest type. */
    public static By byType(String type) {
        return IosBy.predicate("type == " + quote(type));
    }

    /** True when a labelled, enabled, visible element exists. */
    public static By tappable(String label) {
        return IosBy.predicate(
                "label == " + quote(label) + " AND enabled == true AND visible == true");
    }

    // ---------------------------------------------------------------- typed inputs

    /** SwiftUI text input surfaces as {@code XCUIElementTypeTextField}. */
    public static By textField() {
        return byType("XCUIElementTypeTextField");
    }

    /**
     * {@code XCUIElementTypePickerWheel} — step 7 (year of birth) and step 9
     * (eating window), which both render a real {@code UIPickerView}.
     */
    public static By pickerWheel() {
        return byType("XCUIElementTypePickerWheel");
    }

    /**
     * The numeric readout that sits directly above a ruler.
     *
     * <p>Selected structurally rather than by text: the readout is the only
     * {@code StaticText} on steps 17-19 whose label parses as a number, because
     * the ruler's own tick labels are painted into a SwiftUI {@code Canvas} and
     * never reach the accessibility tree.
     */
    public static By numericReadout() {
        return byType("XCUIElementTypeStaticText");
    }

    // ---------------------------------------------------------------- system dialogs

    /** HealthKit / notification prompts are SpringBoard alerts, not app windows. */
    public static By alert() {
        return byType("XCUIElementTypeAlert");
    }

    public static By alertButton(String label) {
        return byExactLabel(label);
    }

    /**
     * The HealthKit authorization sheet, on any of its pages, across iOS
     * releases.
     *
     * <p>The copy differs by release: iOS 26 asks "… would like to access and
     * update your Health data"; iOS 27 asks "Allow “…” to access your Health
     * data?" and adds a second "How much data would you like to share…" page.
     * Apple's own {@code UIA.Health.*} identifiers on the sheet's controls are
     * not localised, so they are matched too.
     */
    public static By healthAuthSheet() {
        return IosBy.predicate(
                "label CONTAINS[c] 'would like to access and update your Health data'"
                        + " OR label CONTAINS[c] 'to access your Health data'"
                        + " OR label CONTAINS[c] " + quote(HEALTH_DATA_SCOPE_HEADING)
                        + " OR name BEGINSWITH 'UIA.Health.'");
    }

    /**
     * iOS 27's second HealthKit page, asking how much history to share. Its
     * Allow button stays disabled until a scope is chosen.
     */
    public static final String HEALTH_DATA_SCOPE_HEADING = "How much data would you like to share";

    /** The per-category switches on the HealthKit sheet (stable UIA names). */
    public static By healthCategorySwitches() {
        return IosBy.predicate("type == 'XCUIElementTypeSwitch' AND name BEGINSWITH 'UIA.Health.'");
    }

    // ---------------------------------------------------------------- tab bar (post-onboarding)

    /**
     * The main tab shell shown once onboarding commits.
     *
     * <p>The app draws a custom floating bar, not a {@code UITabBar}: the live
     * tree has no {@code XCUIElementTypeTabBar}, only sibling
     * {@code XCUIElementTypeButton}s labelled Home / Meals / Insights / Profile.
     * This matches the Home tab (or a native tab bar, should the app adopt
     * one); {@link #MAIN_TABS} lists the rest for a fuller check.
     */
    public static By tabBar() {
        return IosBy.predicate("type == 'XCUIElementTypeTabBar'"
                + " OR (type == 'XCUIElementTypeButton' AND label == 'Home')");
    }

    /** Labels of the custom tab-shell buttons, verified against a live tree capture. */
    public static final java.util.List<String> MAIN_TABS =
            java.util.List.of("Home", "Meals", "Insights", "Profile");

    public static By tab(String label) {
        return AppiumBy.accessibilityId(label);
    }

    /** A tab-bar button by its label; the Button, not the StaticText inside it. */
    public static By tabButton(String label) {
        return byTypeAndLabel("XCUIElementTypeButton", label);
    }

    // ---------------------------------------------------------------- home dashboard
    //
    // Labels from Features/Home/HomeView.swift and a live tree capture of a
    // freshly onboarded user. The app has no accessibility identifiers.

    /** Header button, {@code HomeView.swift:872}. Only rendered on Home. */
    public static By homeCalendarButton() {
        return byTypeAndLabel("XCUIElementTypeButton", "Open calendar");
    }

    /** Header title: "Today, Oct 1" / "Yesterday, Sep 30" ({@code HomeView.swift:908-918}). */
    public static By homeHeaderTitle() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText'"
                + " AND (label BEGINSWITH 'Today, ' OR label BEGINSWITH 'Yesterday, ')");
    }

    /** Hour-based greeting under the title ({@code HomeView.swift:891-901}). */
    public static By homeGreeting(List<String> greetings) {
        StringBuilder in = new StringBuilder();
        for (String greeting : greetings) {
            in.append(in.length() == 0 ? "" : ", ").append(quote(greeting));
        }
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label IN {" + in + "}");
    }

    /** Caption of the calorie card; its number is the next StaticText. */
    public static By homeRemainingCaption() {
        return byTypeAndLabel("XCUIElementTypeStaticText", "Remaining");
    }

    /** "Goal 2234 kcal ›" — opens Calorie Detail ({@code HomeView.swift:1353}). */
    public static By homeGoalButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton'"
                + " AND label BEGINSWITH 'Goal ' AND label ENDSWITH ' kcal'");
    }

    /** ⓘ next to the goal ({@code HomeView.swift:1377}). */
    public static By homeGoalInfoButton() {
        return byTypeAndLabel("XCUIElementTypeButton", "How your goal is set");
    }

    /** "N of 4 streaks active today" badge ({@code HomeView.swift:2104}). */
    public static By homeStreakBadge() {
        return byTypeAndLabelContaining("XCUIElementTypeButton", "streaks active today");
    }

    /**
     * A Home tile whose whole card is one Button, labelled by its title first:
     * {@code WEIGHT, 70.0, kg}, {@code MOOD, Log mood, …}, {@code STEPS, …},
     * {@code AI INSIGHT, …}, {@code INTERMITTENT FASTING, …}.
     */
    public static By homeTile(String titlePrefix) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label BEGINSWITH "
                + quote(titlePrefix));
    }

    /**
     * Home's fasting entry, one button in either of its two states: the
     * "INTERMITTENT FASTING, …" invite, or — once a plan has a fast ahead —
     * the "Upcoming fast, ·, 16:8 protocol, Start, …" card (app c3270fe,
     * {@code FastingEntryRow.swift}). Both open the same screens.
     */
    public static By homeFastingEntry() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'INTERMITTENT FASTING' "
                + "OR label BEGINSWITH 'Upcoming fast')");
    }

    /**
     * Everything {@code HomePage.closeOpenSheets} may have to dismiss, in one
     * query: its buttons by label, an action sheet, a context-menu preview.
     * Empty means there is nothing to close — one round trip instead of twelve.
     */
    public static By anythingToDismiss(List<String> buttonLabels) {
        String labels = buttonLabels.stream().map(Locators::quote).collect(java.util.stream.Collectors.joining(","));
        return IosBy.predicate("(type == 'XCUIElementTypeButton' AND label IN {" + labels + "})"
                + " OR type == 'XCUIElementTypeSheet' OR (type == 'XCUIElementTypeOther' AND name == 'Preview')");
    }

    /** The Activities card's "+ Add" — name "Add", unlike the "+" buttons named "plus". */
    public static By homeActivitiesAddButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == 'Add' AND label == 'Add'");
    }

    /** A plain text on screen, matched exactly. */
    public static By staticText(String label) {
        return byTypeAndLabel("XCUIElementTypeStaticText", label);
    }

    /** A button matched exactly by label. */
    public static By button(String label) {
        return byTypeAndLabel("XCUIElementTypeButton", label);
    }

    /**
     * A week-strip day pill, labelled "1, Thu". Today and past days are
     * Buttons; future days drop the button trait and report as Other.
     */
    public static By homeDayPill(String label) {
        return IosBy.predicate("label == " + quote(label));
    }

    /** The main vertical scroll view; the week strip is a nested one after it. */
    public static By scrollView() {
        return byType("XCUIElementTypeScrollView");
    }

    /**
     * The force-update cover ({@code ForceUpdateOverlay.swift}). It cannot be
     * dismissed, so a test that sees it can only report it.
     */
    public static By forceUpdateCover() {
        return IosBy.predicate("label == 'Update Required' OR label == 'Update Now'");
    }

    // ---------------------------------------------------------------- create menu + food logging
    //
    // Labels verified against a live tree on the simulator (iOS 26.3).

    /**
     * Every "+" button labelled "Add" on Home: the round create button and
     * the water card's two "+" buttons share name and label, and their tree
     * order changes with scrolling. {@code HomePage} picks the widest (62pt
     * create button vs 32pt water buttons).
     */
    public static By homeCreateButtonCandidates() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == 'plus' AND label == 'Add'"
                + " AND visible == true");
    }

    /** Create-menu item: "Fridge Scan", "Workout", "Meal Plan", "Search", "Voice", "Scan". */
    public static By createMenuItem(String label) {
        return button(label);
    }

    /** "Add Food" search screen title ({@code ManualSearchView.swift:230}). */
    public static By foodSearchTitle() {
        return staticText("Add Food");
    }

    /** The search bar ({@code .searchable}, placeholder "Search foods..."). */
    public static By foodSearchField() {
        return byType("XCUIElementTypeSearchField");
    }

    /**
     * A food row in search results or Home's timeline, by its exact name.
     * Rows are single Buttons whose label lists the emoji first and the name
     * second — {@code "🍌, Banana, 1 medium, 105 kcal, …"} — so matching
     * {@code ", Banana, "} cannot hit "Banana Bread" or "Pineapple".
     */
    public static By foodRow(String name) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label CONTAINS "
                + quote(", " + name + ", "));
    }

    /**
     * Built-in ("Local") food rows whose label contains {@code fragment}. Local
     * rows read "… 78 kcal, P: 6g, …"; backend (USDA) rows read "P:6 C:0" with
     * no "kcal, P: ", so this never counts backend results.
     */
    public static By localFoodRows(String fragment) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label CONTAINS " + quote(fragment)
                + " AND label CONTAINS ' kcal, P: '");
    }

    /** Diet filter pill: "Veg", "Egg", "Fish", "Non-Veg", "Vegan". */
    public static By dietPill(String label) {
        return button(label);
    }

    /** System empty state for a search with no hits: "No Results for “x”". */
    public static By foodNoResults() {
        return IosBy.predicate("label BEGINSWITH 'No Results'");
    }

    /** Shown when diet pills hide every hit ({@code ManualSearchView.swift:174}). */
    public static By foodFilteredOut() {
        return staticText("Nothing matches your food filters");
    }

    /** Log sheet's live calorie unit; its number is the text just before it. */
    public static By logSheetKcalUnit() {
        return staticText("kcal");
    }

    /** Log sheet macro chip, e.g. "C:27g" for prefix "C:". */
    public static By logSheetMacro(String prefix) {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label BEGINSWITH "
                + quote(prefix));
    }

    /** Log sheet quantity field (decimal pad, empty label). */
    public static By logSheetQuantityField() {
        return visibleTextField();
    }

    /** Log sheet stepper: name "plus" / "minus" (labels "Add" / "Remove"). */
    public static By logSheetStepper(String name) {
        return visibleButtonNamed(name);
    }

    /** The visible text field of a sheet (Home's fields stay in the tree underneath). */
    public static By visibleTextField() {
        return IosBy.predicate("type == 'XCUIElementTypeTextField' AND visible == true");
    }

    /**
     * A visible Button by its name. Needed where labels collide: the activity
     * sheets' "+" is named "plus" but labelled "Add", like their Add button
     * (named "Add").
     */
    public static By visibleButtonNamed(String name) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == " + quote(name)
                + " AND visible == true");
    }

    /**
     * Header of a meal card in Home's timeline — "BREAKFAST", "LUNCH",
     * "DINNER", "SNACK" — followed by its rows in the tree.
     */
    public static final List<String> MEAL_CARD_HEADERS = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    /** Streak celebration after a first qualifying log: "N-day streak!". */
    public static By streakCelebration() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label CONTAINS 'day streak'");
    }

    /**
     * StoreKit's "Enjoying Cal Care?" review prompt. Suppressed by
     * {@code -disableReviewPrompt}; handled anyway in case a relaunch drops it.
     */
    public static By reviewPromptNotNow() {
        return button("Not Now");
    }

    // ---------------------------------------------------------------- logged food detail (edit / delete)
    //
    // Verified on the simulator: the detail screen's icon buttons are named
    // chevron.left / pencil / trash with labels "Back" / "Edit" / "Trash".

    /** The delete confirmation, a standard alert ({@code MealTimelineView.swift:794}). */
    public static By deleteEntryAlert() {
        return IosBy.predicate("type == 'XCUIElementTypeAlert' AND label == 'Delete this entry?'");
    }

    /** A button inside the on-screen alert, so "Cancel" can never hit a sheet behind it. */
    public static By alertActionButton(String label) {
        return IosBy.classChain("**/XCUIElementTypeAlert/**/XCUIElementTypeButton[`label == "
                + quote(label) + "`]");
    }

    // ---------------------------------------------------------------- water, weight, mood (Module 5)
    //
    // Verified on the simulator (iOS 26.3). Home keeps its controls in the tree
    // beneath these sheets, so sheet lookups use visible matches only.

    /** Water tile total, e.g. "250/2000ml" ({@code WaterTracker.swift:243}). */
    public static By waterTileTotal() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label MATCHES '[0-9]+/[0-9]+ml'");
    }

    /** The water tile's "−" — the only Button named "minus" on Home (label "Remove"). */
    public static By waterRemoveButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == 'minus' AND label == 'Remove'");
    }

    /**
     * Every visible small "+" on Home: the water tile's header "+" (opens the
     * sheet; upper) and its stepper "+" (adds a glass; lower). Both are named
     * "plus", labelled "Add", 32pt wide; the round create button is 62pt.
     */
    public static By homePlusButtons() {
        return homeCreateButtonCandidates();
    }

    /** Water sheet header ({@code WaterTracker.swift:72-98}). */
    public static By waterSheetTitle() {
        return staticText("HYDRATION");
    }

    /** The 8 tappable glasses in the water sheet (Images named "glass"). */
    public static By waterSheetGlasses() {
        return IosBy.predicate("type == 'XCUIElementTypeImage' AND name == 'glass' AND visible == true");
    }

    /** iOS's drag handle on a sheet; swiping it down dismisses the sheet. */
    public static By sheetGrabber() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == 'Sheet Grabber'");
    }

    /** A visible Button with this exact label (sheets over Home reuse "Add" / "Remove" / "Done"). */
    public static By visibleButton(String label) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label == " + quote(label)
                + " AND visible == true");
    }

    /** Weight sheet unit menu: a Button labelled "kg" or "lbs". */
    public static By weightUnitMenu() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND (label == 'kg' OR label == 'lbs')"
                + " AND visible == true");
    }

    /** Weight sheet status after logging: "Updated today's weight" or "Logged". */
    public static By weightLoggedStatus() {
        return IosBy.predicate("label == \"Updated today's weight\" OR label == 'Logged'");
    }

    /** Mood or energy option, a Button labelled "emoji, word" — e.g. "🙂, Good", "💪, High". */
    public static By moodOption(String word) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label ENDSWITH " + quote(", " + word)
                + " AND visible == true");
    }

    // ---------------------------------------------------------------- activities (Module 6)
    //
    // Verified on the simulator (iOS 26.3): catalog rows are Buttons labelled
    // with the activity name inside the list's Cells; the duration sheets show
    // "30 min" and "345 kcal"; Log Exercise rows are single Buttons labelled
    // "Running, 30 min · 12:49 PM, 345 kcal".

    /** Visible rows of the "Log activities" list, including "Describe" and "Recent". */
    public static By activityListRows() {
        return IosBy.classChain("**/XCUIElementTypeCollectionView/**/XCUIElementTypeCell/**"
                + "/XCUIElementTypeButton[`visible == 1`]");
    }

    /** The duration readout of an activity sheet, e.g. "30 min" (rows read "30 min · …"). */
    public static By activityDuration() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label MATCHES '[0-9]+ min'"
                + " AND visible == true");
    }

    /** Every logged-activity row on the Log Exercise screen. */
    public static By exerciseRows() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label MATCHES '.+, [0-9]+ min · .+, [0-9]+ kcal'"
                + " AND visible == true");
    }

    /** The Log Exercise row for {@code name}. */
    public static By exerciseRow(String name) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label BEGINSWITH " + quote(name + ", ")
                + " AND label MATCHES '.+, [0-9]+ min · .+, [0-9]+ kcal' AND visible == true");
    }

    /** An alert by its title, e.g. "Delete activity?" or "End workout?". */
    public static By alertTitled(String title) {
        return IosBy.predicate("type == 'XCUIElementTypeAlert' AND label == " + quote(title));
    }

    /** The live workout clock, "00:10" (or "1:00:10" past an hour). */
    public static By liveWorkoutTimer() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label MATCHES '([0-9]+:)?[0-9]{2}:[0-9]{2}'"
                + " AND visible == true");
    }

    // ---------------------------------------------------------------- fasting (Module 7)
    //
    // Verified on the simulator (iOS 26.3): protocol cards are Buttons
    // labelled "16, Hours, 16:8 - Classic, …"; the schedule sheet previews
    // "Fast 1:32 PM → 5:32 AM"; weekday chips are Buttons "S","M","T","W",
    // "T","F","S"; delete confirmations are action sheets (XCUIElementTypeSheet)
    // whose Cancel iOS hides — they close by tapping outside.

    /** The five preset cards plus the custom card on "Select Fasting Protocol". */
    public static By fastingProtocolCards() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND (label MATCHES '[0-9]+, Hours, .*'"
                + " OR label BEGINSWITH 'Add Custom') AND visible == true");
    }

    /** A protocol card by its title start, e.g. "16:8 - " or "Custom Plan". */
    public static By fastingProtocolCard(String titlePrefix) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label CONTAINS "
                + quote(", Hours, " + titlePrefix) + " AND visible == true");
    }

    /** The custom card before a custom plan is set ("Add Custom, …") or after ("6, Hours, Custom Plan, …"). */
    public static By fastingCustomCard() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'Add Custom'"
                + " OR label CONTAINS ', Hours, Custom Plan') AND visible == true");
    }

    /** Schedule preview line, "Fast 1:32 PM → 5:32 AM" or "Eat …" ({@code prefix} "Fast " / "Eat "). */
    public static By fastingPreview(String prefix) {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label BEGINSWITH " + quote(prefix)
                + " AND label CONTAINS '→' AND visible == true");
    }

    /** The seven weekday chips of "Selected days", single letters. */
    public static By fastingWeekdayChips() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label MATCHES '[SMTWF]' AND visible == true");
    }

    /** Title of Home's fasting card, in any of its three states. */
    public static By fastingCardTitle() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND (label == 'Fasting in progress'"
                + " OR label == 'Fast complete!' OR label == 'Fast ended')");
    }

    /**
     * Home's fasting banner (app 1.0.15): a fixed button above the tab bar,
     * title-case {@code 'Intermittent Fasting, …'} — distinct from the all-caps
     * {@code 'INTERMITTENT FASTING, …'} invite tile and the {@code 'Upcoming fast, …'}
     * card. Running: {@code 'Intermittent Fasting, Fasting in progress · 16:8 protocol, 00:00:05'};
     * ended: {@code 'Intermittent Fasting, Share Your Fast, Ended'}; completed:
     * {@code 'Intermittent Fasting, Share Your Fast, Completed'}. Tapping it opens
     * the detail sheet ({@code Fasting Started / Fasting End / Elapsed / End Fast}).
     */
    public static By fastingBanner() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label BEGINSWITH 'Intermittent Fasting, '");
    }

    /** An action sheet (confirmation dialog) by its message. */
    public static By sheetTitled(String message) {
        return IosBy.predicate("type == 'XCUIElementTypeSheet' AND label == " + quote(message));
    }

    /** A button inside the on-screen action sheet. */
    public static By sheetButton(String label) {
        return IosBy.classChain("**/XCUIElementTypeSheet/**/XCUIElementTypeButton[`label == "
                + quote(label) + "`]");
    }

    /** The area outside a popover-style action sheet; tapping it dismisses the sheet. */
    public static By popoverDismissRegion() {
        return IosBy.predicate("name == 'PopoverDismissRegion'");
    }

    /** The lifted row of an open long-press (context) menu. */
    public static By contextMenuPreview() {
        return IosBy.predicate("type == 'XCUIElementTypeOther' AND name == 'Preview'");
    }

    /** Texts of the on-screen alert: its title, then its message. */
    public static By alertTexts() {
        return IosBy.classChain("**/XCUIElementTypeAlert/**/XCUIElementTypeStaticText");
    }

    // ---------------------------------------------------------------- meal plans (Module 8)
    //
    // Verified on the simulator (iOS 26.3): plan cards carry "Plan Preview",
    // the name, "7 days" and a "Generate" (or "Current plan") Button; the
    // preview's meal cards are Others labelled "Breakfast. Greek Yogurt with
    // Berries. 240 kcal"; My Plan's meal card is a Button "Name, 559 kcal,
    // 1 serving"; its days are "Today", "Day, 2" …; the meal detail's button
    // reads "Log this meal, 559 kcal" or "Future day — can't log yet, 550 kcal".

    /** Today's meal cards on a plan preview. */
    public static By planPreviewMeals() {
        return IosBy.predicate("type == 'XCUIElementTypeOther'"
                + " AND label MATCHES '(Breakfast|Lunch|Snack|Dinner)[.] .+[.] [0-9]+ kcal'");
    }

    /** The visible meal card on My Plan. */
    public static By myPlanMealCard() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label MATCHES '.+, [0-9,]+ kcal, [0-9]+ servings?'"
                + " AND visible == true");
    }

    /** My Plan's eaten / target line, "559/2,234 kcal". */
    public static By myPlanDayTotal() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label MATCHES '[0-9,]+/[0-9,]+ kcal'"
                + " AND visible == true");
    }

    /** A meal-slot tab on My Plan, e.g. "🍳, Breakfast". */
    public static By myPlanSlotTab(String slot) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label ENDSWITH " + quote(", " + slot)
                + " AND visible == true");
    }

    /** The meal detail's log button, whatever state it is in. */
    public static By planMealLogButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND (label BEGINSWITH 'Log this meal'"
                + " OR label BEGINSWITH 'Future day' OR label BEGINSWITH 'Logged') AND visible == true");
    }

    /** The plan-generation cover's title, "Building your Mediterranean Wellness". */
    public static By planGenerationTitle() {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label BEGINSWITH 'Building your '");
    }

    // ---------------------------------------------------------------- profile & settings (Module 9)
    //
    // Verified on the simulator (iOS 26.3): Profile's toolbar holds "Reset Goals"
    // (pencil) and the gear (label "gearshape.fill"); switches carry their row
    // label ("Water reminders", "Apple Health, Connected"); Privacy Policy, Terms
    // and About Us are web pages inside a sheet.

    /** Profile's gear button, which opens Settings. */
    public static By profileSettingsButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == 'gearshape.fill' AND visible == true");
    }

    /** A switch by its row label. */
    public static By switchLabelled(String label) {
        return IosBy.predicate("type == 'XCUIElementTypeSwitch' AND label == " + quote(label));
    }

    /**
     * The switch control inside a labelled row. The labelled Switch spans the
     * whole row and a tap on its centre lands on the label, which does not
     * toggle; the inner, unlabelled Switch is the control itself.
     */
    public static By switchControl(String label) {
        return IosBy.classChain("**/XCUIElementTypeSwitch[`label == " + quote(label) + "`]/XCUIElementTypeSwitch");
    }

    /** The visible element of {@code type}, e.g. a sheet's Slider. */
    public static By visibleOfType(String type) {
        return IosBy.predicate("type == " + quote(type) + " AND visible == true");
    }

    /** A visible text containing {@code fragment} — hidden screens underneath stay in the tree. */
    public static By visibleTextContaining(String fragment) {
        return IosBy.predicate("type == 'XCUIElementTypeStaticText' AND label CONTAINS[c] " + quote(fragment)
                + " AND visible == true");
    }

    /** Profile's Apple Health switch, "Apple Health, Connected" / "Apple Health, Not connected". */
    public static By appleHealthSwitch() {
        return IosBy.predicate("type == 'XCUIElementTypeSwitch' AND label BEGINSWITH 'Apple Health'");
    }

    /** The switch control inside the Apple Health row (see {@link #switchControl}). */
    public static By appleHealthSwitchControl() {
        return IosBy.classChain("**/XCUIElementTypeSwitch[`label BEGINSWITH 'Apple Health'`]/XCUIElementTypeSwitch");
    }

    /**
     * The icon inside a header Button labelled {@code label}. Such a Button
     * spans the whole row while only its text and icon take taps, so a tap
     * on its centre lands on empty space.
     */
    public static By buttonIcon(String label) {
        return IosBy.classChain("**/XCUIElementTypeButton[`label == " + quote(label) + "`]/XCUIElementTypeImage");
    }

    /** Text inside a web view (a loaded web page's content). */
    public static By webViewTexts() {
        return IosBy.classChain("**/XCUIElementTypeWebView/**/XCUIElementTypeStaticText");
    }

    // ---------------------------------------------------------------- tab landing markers

    /** Meals tab with no active plan ({@code MealsTabRoot.swift:151-154}). */
    public static By mealsLanding() {
        return staticText("Available Meal Plans");
    }

    /** First Insights card ({@code InsightsView.swift:404}). */
    public static By insightsLanding() {
        return staticText("This Week vs Last Week");
    }

    /** First Profile card header ({@code ProfileView.swift:1120}). */
    public static By profileLanding() {
        return staticText("About You");
    }

    /** The scan result's log button: "Log" for one item, "Log 2 items" for more ({@code ScanResultView.swift}). */
    public static By scanLogButton() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND visible == true AND "
                + "(label == 'Log' OR label MATCHES 'Log [0-9]+ items')");
    }

    /** The scan result's meal chip: "Lunch, AUTO, Tap to change", "Dinner, Tap to change". */
    public static By scanMealChip() {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND label MATCHES "
                + "'(Breakfast|Lunch|Dinner|Snack), .*Tap to (change|set all)'");
    }

    /** Every element of {@code type}, visible or not. */
    public static By ofType(String type) {
        return IosBy.predicate("type == " + quote(type));
    }

    /** A button by its name (an SF Symbol name for icon buttons), visible or not. */
    public static By buttonNamed(String name) {
        return IosBy.predicate("type == 'XCUIElementTypeButton' AND name == " + quote(name));
    }
}
