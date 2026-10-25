package com.portfolio.ios.core;

import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The three gestures this app needs that a plain {@code tap()} cannot express.
 *
 * <p>Kept out of {@code BasePage} so widget helpers can use them without
 * depending on page state.
 */
public final class Gestures {

    /**
     * Row budget for a picker-wheel walk. The widest wheel in this app is the
     * 78-row birth-year picker, so any reachable target is at most 78 nudges
     * away; 120 leaves headroom without letting a genuinely broken wheel spin
     * indefinitely.
     */
    private static final int WHEEL_MAX_STEPS = 120;

    private Gestures() {
    }

    /**
     * Press and hold for a sustained period.
     *
     * <p>Step 24 ({@code tap_and_hold}) commits only on a ~1.3s hold wired to
     * {@code DragGesture(minimumDistance: 0)}, and cancels if the finger lifts
     * early. Callers should therefore always pass a duration comfortably longer
     * than the app's own threshold.
     */
    public static void pressAndHold(IOSDriver driver, WebElement element, Duration hold) {
        // Zero inter-action pause: the app starts its timer on pointer-down, so
        // a pause inserted before the hold begins only inflates wall-clock time.
        Actions actions = new Actions(driver, Duration.ZERO);
        actions.moveToElement(element);
        actions.clickAndHold();
        actions.pause(hold);
        actions.release();
        actions.perform();
    }

    /**
     * Drag from the element's centre by {@code (dx, dy)} points, in slices.
     *
     * <p>Slicing matters: a single large jump can be coalesced by the platform
     * gesture recogniser, whereas intermediate waypoints produce the stream of
     * translation updates SwiftUI's {@code DragGesture} needs to keep firing
     * {@code onChanged}.
     */
    public static void drag(IOSDriver driver, WebElement element, int dx, int dy) {
        Actions actions = new Actions(driver, Duration.ZERO);
        actions.moveToElement(element);
        actions.clickAndHold();
        int slices = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / 40);
        int stepX = dx / slices;
        int stepY = dy / slices;
        for (int i = 0; i < slices; i++) {
            actions.moveByOffset(stepX, stepY);
        }
        actions.release();
        actions.perform();
    }

    /**
     * Long-press an element as one continuous native touch.
     *
     * <p>Used when a Selenium Actions press-and-hold does not commit — WDA can
     * deliver the press, pause, and release as separate gestures that a
     * {@code DragGesture(minimumDistance: 0)} timer ignores. {@code mobile:
     * touchAndHold} presses a single touch for the full duration instead.
     *
     * @param hold total press duration; must exceed the app's own threshold
     */
    public static void touchAndHold(IOSDriver driver, WebElement element, Duration hold) {
        Map<String, Object> params = new HashMap<>();
        params.put("element", ((RemoteWebElement) element).getId());
        params.put("duration", hold.toMillis() / 1000.0);
        ((JavascriptExecutor) driver).executeScript("mobile: touchAndHold", params);
    }

    /**
     * Long-presses an element to open its context menu ({@code mobile:
     * touchAndHold} on the element, keyed {@code elementId} as XCUITest
     * driver 12 expects).
     */
    public static void longPress(IOSDriver driver, WebElement element, Duration hold) {
        Map<String, Object> params = new HashMap<>();
        params.put("elementId", ((RemoteWebElement) element).getId());
        params.put("duration", hold.toMillis() / 1000.0);
        ((JavascriptExecutor) driver).executeScript("mobile: touchAndHold", params);
    }

    /**
     * Scrolls a scrollable element (table, scroll view) by about one page.
     *
     * <p>Element-anchored, so it needs no screen coordinates. Used to bring
     * controls that iOS has not materialised in the tree yet — e.g. the iOS 27
     * HealthKit sheet's bottom Allow button — into view before resolving them.
     *
     * @param direction {@code down} reveals content further down
     */
    public static void scroll(IOSDriver driver, WebElement scrollable, String direction) {
        Map<String, Object> params = new HashMap<>();
        params.put("elementId", ((RemoteWebElement) scrollable).getId());
        params.put("direction", direction);
        ((JavascriptExecutor) driver).executeScript("mobile: scroll", params);
    }

    /**
     * Scrolls by a fraction of the element's height.
     *
     * <p>Use a fraction below 1 when scrolling until something appears: a full
     * page can carry a short element from below the fold to above it in one
     * move, so it is never seen on screen.
     *
     * @param distance 0 &lt; distance ≤ 1, relative to the scrollable's height
     */
    public static void scroll(IOSDriver driver, WebElement scrollable, String direction, double distance) {
        Map<String, Object> params = new HashMap<>();
        params.put("elementId", ((RemoteWebElement) scrollable).getId());
        params.put("direction", direction);
        params.put("distance", distance);
        ((JavascriptExecutor) driver).executeScript("mobile: scroll", params);
    }

    /**
     * Tap an absolute point.
     *
     * <p>Used for system permission sheets, where tapping the resolved button's
     * center coordinates is more reliable than delegating to Selenium's element
     * click: SpringBoard sheets live outside the app hierarchy, and an element
     * click that reports success can still leave the sheet on screen.
     */
    public static void tapAt(IOSDriver driver, int x, int y) {
        Actions actions = new Actions(driver, Duration.ZERO);
        actions.moveToLocation(x, y);
        actions.click();
        actions.perform();
    }

    /**
     * Drag starting from an absolute point, for widgets that cannot be addressed
     * as an element at all.
     *
     * <p>Needed by {@code RulerScalePicker}: it is a SwiftUI {@code Canvas}, which
     * the accessibility tree reports as an unnamed container with no bounds
     * worth trusting, so a hit point derived from the neighbouring readout's
     * frame is more dependable than trying to locate it.
     */
    public static void dragAt(IOSDriver driver, int startX, int startY, int dx, int dy) {
        Actions actions = new Actions(driver, Duration.ZERO);
        actions.moveToLocation(startX, startY);
        actions.clickAndHold();
        int slices = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / 40);
        int stepX = dx / slices;
        int stepY = dy / slices;
        for (int i = 0; i < slices; i++) {
            actions.moveByOffset(stepX, stepY);
        }
        actions.release();
        actions.perform();
    }

    /**
     * Selects {@code value} on the wheel located by {@code wheel}.
     *
     * <p><b>Why this loops instead of one {@code selectPickerWheelValue} call.</b>
     * {@code mobile: selectPickerWheelValue} is documented to scroll until it
     * finds a row whose text matches. It cannot do that here: this app's pickers
     * are SwiftUI {@code Picker}s, and SwiftUI renders each row as a hosting
     * view rather than a native {@code UIPickerView} label. The live tree bears
     * this out — the birth-year wheel exposes 78 children, all
     * {@code XCUIElementTypeOther} with a {@code null} label, and zero
     * descendant {@code StaticText}s. The app's own source comments acknowledge
     * this, and note that adding per-row styling here previously crashed
     * {@code layoutSublayersOfLayer:}.
     *
     * <p>With no readable row text, the driver's matcher finds nothing to
     * compare and the command degenerates into a single-row nudge in the
     * direction given by {@code order}. Observed directly: asking for 1990 from
     * 2001 moved the wheel to 2002, then 2003, then 2004 — one row per call,
     * every time, target ignored.
     *
     * <p>What <i>is</i> readable is the wheel's own {@code value} attribute,
     * which reports the selected row. So this drives the same primitive as a
     * directional step and closes the loop against that attribute: nudge toward
     * the target until the reported value equals it. Deterministic, and bounded
     * by the row count, since a 78-row wheel is at most 78 nudges.
     *
     * <p>The wheel is re-resolved from {@code wheel} on every iteration, never
     * held across one. Each selection change makes SwiftUI rebuild all 78
     * hosting views, so a reference captured before the first nudge is stale by
     * the second — a real {@code StaleElementReferenceException} against this
     * app's step 7 and step 9.
     *
     * @param sortKey maps a row's displayed value to its position on the wheel.
     *               Needed because the app's wheels are not all numeric — the
     *               eating window holds times like "8:00 am", which cannot be
     *               ordered by string comparison.
     */
    public static void setPickerWheelValue(IOSDriver driver, Supplier<WebElement> wheel, String value,
                                           ToIntFunction<String> sortKey) {
        String target = value.trim();
        int targetKey = sortKey.applyAsInt(target);
        String current = readWheelValue(driver, wheel.get());
        if (target.equals(current)) {
            return;
        }
        String previous = current;

        for (int step = 0; step < WHEEL_MAX_STEPS; step++) {
            // Rows ascend down the wheel, so a larger sort key lies further down.
            // 'next' walks downward, 'previous' upward.
            int currentKey = sortKey.applyAsInt(current);
            if (currentKey == targetKey) {
                return;
            }
            String order = currentKey > targetKey ? "previous" : "next";
            nudgeWheel(driver, wheel.get(), order);
            current = readWheelValue(driver, wheel.get());
            if (target.equals(current) || current.equals(previous)) {
                // Either matched, or the wheel wrapped past its end without ever
                // hitting the target — a wrap always revisits a value.
                break;
            }
            previous = current;
        }
        if (!target.equals(current)) {
            throw new AssertionError(
                    "Picker wheel settled on '" + current + "' after " + WHEEL_MAX_STEPS
                            + " steps, never reaching '" + target + "'. Either the target is not "
                            + "on this wheel or the wheel stopped responding.");
        }
    }

    /** Convenience overload for wheels whose values are plain integers. */
    public static void setPickerWheelValue(IOSDriver driver, Supplier<WebElement> wheel, String value) {
        setPickerWheelValue(driver, wheel, value, Integer::parseInt);
    }

    /**
     * One single-row scroll of the wheel.
     *
     * <p>No {@code offset} parameter: passing one makes Appium 3.6 reject the
     * whole command with {@code InvalidArgumentException}, and it is not needed
     * once direction alone is being used.
     */
    private static void nudgeWheel(IOSDriver driver, WebElement wheel, String order) {        Map<String, Object> params = new HashMap<>();
        params.put("element", ((RemoteWebElement) wheel).getId());
        params.put("order", order);
        params.put("value", "");
        params.put("maxAttempts", 1);
        ((JavascriptExecutor) driver).executeScript("mobile: selectPickerWheelValue", params);
    }

    /**
     * The wheel's selected value.
     *
     * <p>Uses {@code getAttribute("value")} rather than a JavaScript
     * {@code arguments[0].value} read: XCUITest does not implement
     * {@code executeScript} at all and answers "Method is not implemented", so
     * the JS fallback would turn a readable attribute into a hard failure.
     */
    private static String readWheelValue(IOSDriver driver, WebElement wheel) {
        String value = wheel.getAttribute("value");
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        String text = wheel.getText();
        return text == null ? "" : text.trim();
    }
}