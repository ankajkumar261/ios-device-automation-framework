package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.OnboardingStep;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * SpringBoard permission sheets.
 *
 * <p>These are separate system windows, not part of the app's hierarchy, and
 * they must be handled explicitly. The session is created with
 * {@code autoAcceptAlerts=false} precisely so they are not silently swallowed —
 * step 22 offers Apple Health, and a test that never sees the sheet cannot tell
 * the difference between "granted" and "never asked".
 */
public final class SystemDialogPage extends StepPage {

    /** iOS labels differ by OS release; all spellings are accepted. */
    private static final List<String> ALLOW = List.of("Allow", "OK");
    private static final List<String> DENY = List.of("Don't Allow", "Not Now", "Deny");
    /**
     * The non-dismissing toggle that enables every Health category. Tapping it
     * does NOT close the sheet — a subsequent "Allow" is still required. Kept
     * separate from {@link #ALLOW} on purpose: folding it into the allow list
     * makes {@code tapAndDismiss} tap it, wait 2s for a dismissal that never
     * comes, and retry twice more before ever trying the real Allow button.
     */
    private static final List<String> TURN_ON_ALL = List.of("Turn On All", "Turn On All Health Data");
    /**
     * Buttons that acknowledge a follow-up confirmation rather than answering
     * the original prompt. iOS shows "You can turn on health data categories
     * later in the Health app" with a single OK after a deny-with-nothing-
     * selected; the app cannot advance until that is acknowledged.
     */
    private static final List<String> ACKNOWLEDGE = List.of("OK", "Done", "Close");
    /** Apple's non-localised identifier for the HealthKit sheet's grant button (iOS 27). */
    private static final String ALLOW_BUTTON_ID = "UIA.Health.Allow.Button";
    /** Apple's non-localised identifier for the HealthKit sheet's decline button (iOS 27). */
    private static final String DENY_BUTTON_ID = "UIA.Health.DoNotAllow.Button";
    /** Least-history option on iOS 27's "How much data would you like to share" page. */
    private static final String DATA_SCOPE = "Past 30 Days";
    /** The iOS 27 sheet is about two pages tall; this bounds the reveal scroll. */
    private static final int MAX_SHEET_SCROLLS = 4;

    public SystemDialogPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory, OnboardingStep.APPLE_HEALTH);
    }

    /**
     * True when a system prompt is currently on screen.
     *
     * <p>Not every system sheet is an {@code XCUIElementTypeAlert}. The HealthKit
     * authorization sheet reports no Alert node at all — it is a SpringBoard
     * sheet whose distinctive content is the "would like to access and update
     * your Health data" text — so checking only for alerts makes this return
     * false while the sheet is plainly visible, and the caller then skips the
     * deny/allow it came to perform. Observed live: the prompt's buttons were
     * enumerated in the tree while {@code isDialogShowing} insisted nothing was
     * showing.
     *
     * <p>Single-budget poll: both signatures are checked with the non-waiting
     * {@link Waits#isPresent} inside one {@code untilQuietly} loop. The previous
     * form called {@code isVisible(alert, within) || isVisible(healthText,
     * within)} sequentially, paying the full timeout twice (up to 16s for
     * SHORT, 40s for MEDIUM) whenever no dialog was showing — the common case
     * before the Continue tap.
     */
    public boolean isDialogShowing(Duration within) {
        return Waits.untilQuietly(within, () ->
                Waits.isPresent(driver, Locators.alert())
                        || isHealthSheetShowing());
    }

    /** Grants the frontmost permission sheet. */
    @Step("Allow the permission prompt")
    public void allow() {
        tapAndDismiss(ALLOW, "allow");
    }

    /** Denies the frontmost permission sheet. */
    @Step("Deny the permission prompt")
    public void deny() {
        // iOS 27 keeps "Don't Allow" below the fold of the HealthKit sheet;
        // a no-op on plain alerts and on iOS 26, where it is already visible.
        if (isHealthSheetShowing()) {
            revealSheetButton(SystemDialogPage::isDenyButton);
        }
        tapAndDismiss(DENY, "deny");
    }

    /**
     * Grants the Apple Health prompt: "Turn On All" first, then "Allow".
     *
     * <p>Order matters. The HealthKit sheet opens with every category switched
     * off; "Turn On All" only flips the switches and leaves the sheet open,
     * while "Allow" submits the choice and dismisses it. Tapping Allow first
     * submits an all-off selection (which iOS may answer with a "you can turn
     * on categories later" confirmation), so the grant path enables everything
     * before submitting.
     *
     * <p>Handles the optional second sheet ("Turn On All Health Data") the same
     * way: if a HealthKit sheet is still showing after the first Allow, Turn On
     * All is retried and Allow is submitted again. Returns once no HealthKit
     * sheet remains; any plain confirmation alert is left to
     * {@link #acknowledgeRemainingAlert}.
     */
    @Step("Grant Apple Health access (Turn On All, then Allow)")
    public void allowAppleHealth() {
        grantOnce();
        // Optional second sheet where iOS asks which data types to share.
        // Quick probe only — the common case is "already dismissed".
        if (isHealthSheetShowing(Waits.QUICK)) {
            grantOnce();
        }
    }

    /** One Turn On All + Allow round: covers the first sheet and any second one. */
    private void grantOnce() {
        // Best-effort toggle: absent on iOS versions without it, or when the
        // store already has everything enabled. Never fails when missing.
        if (tryTurnOnAll()) {
            tapEnabledAllowButton();
        } else {
            allow();
        }
    }

    /**
     * Taps "Turn On All" without expecting the sheet to dismiss, and verifies
     * the Allow button actually became enabled.
     *
     * @return true when the toggle was found and enabled Allow; false when the
     *         toggle is absent on this sheet (older iOS, already all-on)
     * @throws Waits.WaitTimeout when the toggle is present but Allow never
     *         enables — fail fast here rather than tapping a greyed-out Allow
     *         no-op three times downstream
     */
    @Step("Turn on all Health categories")
    public boolean tryTurnOnAll() {
        if (findTurnOnAllToggle() == null
                && !Waits.untilQuietly(Duration.ofSeconds(2), () -> findTurnOnAllToggle() != null)) {
            return false;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement toggle = findTurnOnAllToggle();
            if (toggle == null) {
                return false;
            }
            tapNatively(toggle);
            // No fixed settle: poll for the toggle's effect instead. iOS 26
            // enables a visible Allow button; iOS 27 keeps its Allow below the
            // fold, so the switches flipping on is the observable signal there.
            // Never re-tap after either: on iOS 27 the row becomes "Deselect
            // N Topics" and a second tap turns everything back off.
            if (Waits.untilQuietly(Duration.ofSeconds(3),
                    () -> !isHealthSheetShowing() || isAllowButtonEnabled()
                            || allHealthSwitchesOn())) {
                return true;
            }
        }
        throw new Waits.WaitTimeout("Turn On All did not enable the Allow button",
                Waits.SHORT,
                new IllegalStateException("buttons on screen: " + visibleButtons()));
    }

    /**
     * The tappable Turn On All row.
     *
     * <p>Preferred target is the row Cell itself (stable UIA name
     * {@code UIA.Health.AuthSheet.AllCategoryButton}, not localised copy):
     * the visible "Turn On All" node is a StaticText whose element-tap may not
     * trigger the row's handler. Falls back to that StaticText when the Cell
     * is not exposed (other iOS versions).
     */
    private WebElement findTurnOnAllToggle() {
        for (WebElement cell : driver.findElements(Locators.byType("XCUIElementTypeCell"))) {
            try {
                String name = cell.getAttribute("name");
                if (name != null && name.contains("AllCategoryButton") && cell.isDisplayed()) {
                    return cell;
                }
            } catch (RuntimeException transientFailure) {
                // Tree mid-animation; skip this node on this pass.
            }
        }
        return findDialogButton(TURN_ON_ALL);
    }

    /** True when the Allow <i>Button</i> exists, shows, and is enabled. */
    private boolean isAllowButtonEnabled() {
        for (WebElement button : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
            try {
                if (isAllowButton(button) && button.isDisplayed()) {
                    return button.isEnabled();
                }
            } catch (RuntimeException transientFailure) {
                // Tree mid-animation; treat as not-enabled on this pass.
            }
        }
        return false;
    }

    /**
     * The sheet's grant button. iOS 26 labels it "Allow"; iOS 27 relabels the
     * same control "Continue" once topics are selected but keeps Apple's
     * stable identifier — matched by identifier there, because the app's own
     * step-22 screen also has a "Continue" button behind the sheet.
     */
    private static boolean isAllowButton(WebElement button) {
        return ALLOW_BUTTON_ID.equals(button.getAttribute("name"))
                || normalize("Allow").equals(normalize(button.getAttribute("label")));
    }

    /** The sheet's decline button, by Apple's identifier (iOS 27) or label. */
    private static boolean isDenyButton(WebElement button) {
        if (DENY_BUTTON_ID.equals(button.getAttribute("name"))) {
            return true;
        }
        String label = normalize(button.getAttribute("label"));
        return DENY.stream().anyMatch(candidate -> normalize(candidate).equals(label));
    }

    /** True when the sheet's category switches exist and every one is on. */
    private boolean allHealthSwitchesOn() {
        List<WebElement> switches = driver.findElements(Locators.healthCategorySwitches());
        if (switches.isEmpty()) {
            return false;
        }
        for (WebElement healthSwitch : switches) {
            try {
                if (!"1".equals(healthSwitch.getAttribute("value"))) {
                    return false;
                }
            } catch (RuntimeException transientFailure) {
                return false;
            }
        }
        return true;
    }

    /** Non-waiting lookup for the displayed, enabled grant button. */
    private WebElement findEnabledAllowButton() {
        for (WebElement button : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
            try {
                if (isAllowButton(button) && button.isDisplayed() && button.isEnabled()) {
                    return button;
                }
            } catch (RuntimeException transientFailure) {
                // Sheet mid-animation; skip this node on this pass.
            }
        }
        return null;
    }

    /**
     * Scrolls the sheet until a button matching {@code matcher} is on screen.
     *
     * <p>iOS 27 places Allow and Don't Allow at the bottom of the sheet's
     * table, below the fold, and does not even materialise them in the tree
     * until scrolled to. No-op on iOS 26, where both are visible from the
     * start. Bounded: the sheet is about two pages tall, and one scroll has
     * been observed to reveal both buttons on hardware.
     */
    private void revealSheetButton(Predicate<WebElement> matcher) {
        for (int page = 0; page < MAX_SHEET_SCROLLS && findDisplayedButton(matcher) == null; page++) {
            WebElement table = firstDisplayed(Locators.byType("XCUIElementTypeTable"));
            if (table == null) {
                return;
            }
            try {
                Gestures.scroll(driver, table, "down");
            } catch (RuntimeException alreadyAtBottom) {
                return;
            }
        }
    }

    private WebElement findDisplayedButton(Predicate<WebElement> matcher) {
        for (WebElement button : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
            try {
                if (matcher.test(button) && button.isDisplayed()) {
                    return button;
                }
            } catch (RuntimeException transientFailure) {
                // Sheet mid-scroll; skip this node on this pass.
            }
        }
        return null;
    }

    private WebElement firstDisplayed(org.openqa.selenium.By by) {
        for (WebElement element : driver.findElements(by)) {
            try {
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (RuntimeException transientFailure) {
                // Tree mid-animation; skip this node on this pass.
            }
        }
        return null;
    }

    /** True while iOS 27's "How much data would you like to share" page shows. */
    private boolean isDataScopePageShowing() {
        return Waits.isPresent(driver,
                Locators.byLabelContaining(Locators.HEALTH_DATA_SCOPE_HEADING));
    }

    /**
     * Answers iOS 27's data-scope page with the least history on offer
     * ("Past 30 Days and Future Data"); its Allow stays disabled until a scope
     * is chosen.
     */
    @Step("Choose the Health data scope (past 30 days)")
    private void chooseDataScope() {
        WebElement scope = Waits.visible(driver,
                Locators.byTypeAndLabelContaining("XCUIElementTypeStaticText", DATA_SCOPE),
                Waits.SHORT, "Health data-scope option '" + DATA_SCOPE + "'");
        tapNatively(scope);
    }

    /**
     * Taps the enabled Allow Button, never its StaticText label twin.
     *
     * <p>The Health sheet renders "Allow" as both a Button and a StaticText,
     * but only the Button tracks the greyed-out state: the StaticText reports
     * enabled even while the action is disabled, so the generic Button-first
     * search degrades into tapping a no-op label exactly when Allow is grey.
     * Waiting for the enabled Button also covers the Turn On All switch-flip
     * animation with a real condition instead of a sleep.
     */
    @Step("Tap the enabled Allow button")
    private void tapEnabledAllowButton() {
        tapNatively(waitForEnabledAllowButton());

        // iOS 27 follows with a "How much data would you like to share" page
        // instead of dismissing. Wait for whichever comes first; on iOS 26 the
        // sheet is simply gone and this returns on the first poll.
        if (Waits.untilQuietly(Duration.ofSeconds(3),
                () -> isDataScopePageShowing() || !isHealthSheetShowing())
                && isDataScopePageShowing()) {
            chooseDataScope();
            tapNatively(waitForEnabledAllowButton());
        }

        if (!Waits.untilQuietly(Duration.ofSeconds(2), () -> !isHealthSheetShowing())) {
            // Enabled tap didn't clear it (second sheet, animation): fall back
            // to the alternating-strategy dismiss.
            tapAndDismiss(ALLOW, "allow");
        }
    }

    /** Reveals the grant button if needed, then waits for it to enable. */
    private WebElement waitForEnabledAllowButton() {
        revealSheetButton(SystemDialogPage::isAllowButton);
        return Waits.until(Waits.SHORT, "enabled Allow button", this::findEnabledAllowButton);
    }

    /** Non-waiting lookup for a dialog button; null when none is on screen. */
    private WebElement findDialogButton(List<String> candidates) {
        for (String type : List.of("XCUIElementTypeButton", "XCUIElementTypeStaticText")) {
            for (String candidate : candidates) {
                for (WebElement element : driver.findElements(Locators.byType(type))) {
                    try {
                        String label = element.getAttribute("label");
                        if (normalize(candidate).equals(normalize(label))
                                && element.isDisplayed()
                                && element.isEnabled()) {
                            return element;
                        }
                    } catch (RuntimeException transientFailure) {
                        // Tree mid-animation; treat as absent on this pass.
                    }
                }
            }
        }
        return null;
    }

    /**
     * Taps a dialog button and verifies the sheet actually dismissed.
     *
     * <p>Verification matters because a system-sheet tap can report success while
     * leaving the sheet visible—observed on HealthKit's "Don’t Allow", where an
     * element click completed without dismissing anything. Without this check,
     * the suite would wait for the next app screen until timeout, far from the
     * tap that failed.
     *
     * <p>"Dismissed" means the HealthKit form sheet is gone. A follow-up
     * confirmation alert ("You can turn on health data categories later…")
     * legitimately appears after a deny-with-nothing-selected and is handled
     * separately by {@link #acknowledgeRemainingAlert}, not mistaken for a
     * failed tap here.
     */
    private void tapAndDismiss(List<String> candidates, String intent) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement button = waitForDialogButton(candidates, intent);
            // Alternate tap strategies across retries. Absolute-coordinate taps
            // (moveToLocation) were observed doing nothing at all on the iOS 26
            // Health sheet — three identical taps fail identically — while a
            // native element tap goes through WDA's element-tap endpoint. One
            // of the two paths lands; repeating the same failing path never
            // does. Odd attempts tap natively, even ones by coordinates.
            if (attempt % 2 == 1) {
                tapNatively(button);
            } else {
                tapButtonCenter(button);
            }
            boolean dismissed = Waits.untilQuietly(Duration.ofSeconds(2),
                    () -> !isHealthSheetShowing());
            if (dismissed) {
                return;
            }
        }
        throw new Waits.WaitTimeout(
                intent + " button " + candidates + " did not dismiss the system dialog",
                Waits.SHORT,
                new IllegalStateException("buttons on screen: " + visibleButtons()));
    }

    /**
     * Acknowledges any follow-up confirmation alert left behind by an
     * allow/deny, e.g. HealthKit's "You can turn on health data categories
     * later in the Health app" OK dialog after denying with nothing selected.
     * No-op when no alert is showing. The app under test cannot advance past
     * step 22 until this is gone, so skipping it strands the flow on a screen
     * that looks — misleadingly — like the deny tap failed.
     */
    @Step("Acknowledge any follow-up confirmation")
    public void acknowledgeRemainingAlert() {
        // Fast probe: the common grant path has no follow-up, so don't pay 3s
        // here on every run. 2s still covers SpringBoard's alert animation.
        if (!Waits.untilQuietly(Duration.ofSeconds(2),
                () -> Waits.isPresent(driver, Locators.alert()))) {
            // A second HealthKit sheet (not a plain alert) can also linger when
            // Allow was tapped with categories still off — dismiss it the same
            // way as the first sheet rather than stranding the flow.
            if (isHealthSheetShowing(Waits.QUICK)) {
                allow();
                acknowledgeRemainingAlert();
            }
            return;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement button = waitForDialogButton(ACKNOWLEDGE, "acknowledge");
            tapNatively(button);
            boolean dismissed = Waits.untilQuietly(Duration.ofSeconds(2),
                    () -> !Waits.isPresent(driver, Locators.alert()));
            if (dismissed) {
                // The alert may have revealed the second Health sheet
                // underneath; clear it too so step 22 can auto-advance.
                if (isHealthSheetShowing(Waits.QUICK)) {
                    allow();
                }
                return;
            }
        }
        throw new Waits.WaitTimeout("follow-up confirmation did not dismiss",
                Waits.SHORT,
                new IllegalStateException("buttons on screen: " + visibleButtons()));
    }

    /**
     * True while the HealthKit authorization form sheet itself is showing —
     * any of its pages, on any supported iOS release (see
     * {@link Locators#healthAuthSheet()}).
     */
    private boolean isHealthSheetShowing() {
        return Waits.isPresent(driver, Locators.healthAuthSheet());
    }

    private boolean isHealthSheetShowing(Duration within) {
        return Waits.untilQuietly(within, this::isHealthSheetShowing);
    }

    /**
     * Declines a SpringBoard alert — e.g. notification permission — if one
     * appears within {@code within}.
     *
     * <p>Goes through WDA's alert endpoint rather than an element lookup:
     * SpringBoard alerts sit outside the app's hierarchy, so neither the page
     * source nor {@link Locators#alert()} can see them. The decline button is
     * picked by label from the alert's own button list, never by position.
     *
     * @return true if an alert was declined, false if none appeared
     */
    @Step("Decline a system alert if one appears")
    public boolean declineSystemAlertIfPresent(Duration within) {
        List<String> buttons;
        try {
            buttons = Waits.until(within, "system alert", () -> {
                List<String> found = systemAlertButtons();
                return found.isEmpty() ? null : found;
            });
        } catch (Waits.WaitTimeout noAlert) {
            return false;
        }
        String decline = buttons.stream()
                .filter(label -> DENY.stream()
                        .anyMatch(candidate -> normalize(candidate).equals(normalize(label))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "system alert offers no decline button " + DENY + ", only " + buttons));
        ((JavascriptExecutor) driver).executeScript("mobile: alert",
                Map.of("action", "dismiss", "buttonLabel", decline));
        Waits.untilTrue(Waits.SHORT, "system alert to close after '" + decline + "'",
                () -> systemAlertButtons().isEmpty());
        return true;
    }

    /** Buttons of the SpringBoard alert on screen; empty when there is none. */
    private List<String> systemAlertButtons() {
        try {
            Object buttons = ((JavascriptExecutor) driver).executeScript("mobile: alert",
                    Map.of("action", "getButtons"));
            if (buttons instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (WebDriverException noAlert) {
            // WDA answers "no such alert" when nothing is showing.
        }
        return List.of();
    }

    /**
     * Finds a dialog choice, checking Buttons before their StaticText labels.
     *
     * <p>HealthKit renders choices as both a Button and a StaticText with the
     * same label. The Button is normally the right target, but if its element
     * click is ignored, the label's coordinates are the useful fallback.
     */
    private WebElement waitForDialogButton(List<String> candidates, String intent) {
        return Waits.until(Waits.SHORT, intent + " button " + candidates, () -> {
            for (String type : List.of("XCUIElementTypeButton", "XCUIElementTypeStaticText")) {
                for (String candidate : candidates) {
                    for (WebElement element : driver.findElements(Locators.byType(type))) {
                        try {
                            String label = element.getAttribute("label");
                            if (normalize(candidate).equals(normalize(label))
                                    && element.isDisplayed()
                                    && element.isEnabled()) {
                                return element;
                            }
                        } catch (RuntimeException transientFailure) {
                            // Sheet mid-animation; skip this element on this pass.
                        }
                    }
                }
            }
            return null;
        });
    }

    /**
     * Native element tap through WDA's element-tap endpoint.
     *
     * <p>Preferred for SpringBoard sheets: unlike an absolute-coordinate tap it
     * carries the element's identity, so it cannot land on the wrong window
     * when the app and system hierarchies overlap. Falls back to a coordinate
     * tap only when the native tap itself throws.
     */
    private void tapNatively(WebElement button) {
        try {
            Waits.resilient(button::click);
            return;
        } catch (RuntimeException nativeTapFailed) {
            // Element tap rejected (e.g. hit-test says obscured mid-animation):
            // fall back to tapping the resolved centre coordinates.
        }
        tapButtonCenter(button);
    }

    private void tapButtonCenter(WebElement button) {
        Rectangle box = Waits.resilientGet(button::getRect);
        Gestures.tapAt(driver, box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2);
    }

    private String visibleButtons() {
        StringBuilder onScreen = new StringBuilder();
        for (WebElement button : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
            try {
                if (button.isDisplayed()) {
                    onScreen.append("'").append(button.getAttribute("label")).append("' ");
                }
            } catch (RuntimeException transientFailure) {
                // A sheet mid-animation can detach elements while diagnostics run.
            }
        }
        return onScreen.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('’', '\'').replace('‘', '\'').trim();
    }
}