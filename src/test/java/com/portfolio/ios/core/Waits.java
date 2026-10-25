package com.portfolio.ios.core;

import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Every wait in the suite goes through here.
 *
 * <p>Two rules this class exists to enforce:
 * <ol>
 *   <li><b>No {@code Thread.sleep}.</b> Sleeping is the single biggest cause of
 *       both flakes and slow suites; every wait below is condition-based.</li>
 *   <li><b>No silent timeouts.</b> Each failure names the condition it gave up
 *       on, so a red run says "Continue button never became visible on step 3"
 *       instead of a bare {@code TimeoutException}.</li>
 * </ol>
 */
public final class Waits {

    public static final Duration SHORT = Duration.ofSeconds(8);
    public static final Duration MEDIUM = Duration.ofSeconds(20);
    /** Step 25 calls the production AI backend; give it room. */
    public static final Duration LONG = Duration.ofSeconds(90);
    public static final Duration AI_BACKEND = Duration.ofSeconds(180);
    /**
     * Fast negative probe for conditions expected to be absent (no dialog, no
     * follow-up alert, no second sheet). A positive match still returns on the
     * first poll; only the failure path pays the timeout, so keep this tight.
     */
    public static final Duration QUICK = Duration.ofSeconds(3);
    /**
     * Budget for a system sheet to appear after its trigger tap. HealthKit's
     * sheet is presented asynchronously by SpringBoard, normally within 1-3s;
     * 10s leaves headroom for a loaded simulator without paying MEDIUM (20s)
     * on every failure.
     */
    public static final Duration DIALOG = Duration.ofSeconds(10);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);
    /**
     * Polling interval for controls whose readiness is an event, not a screen
     * transition.
     *
     * <p>A 400ms interval is fine when waiting for a new screen: screen changes
     * take hundreds of milliseconds anyway. It is the wrong tool when watching
     * one already-visible button flip from disabled to enabled. That transition
     * is effectively instantaneous, so use a tight poll and react to the first
     * state change instead of adding up to a full normal poll cycle after it.
     */
    private static final Duration EVENT_POLL_INTERVAL = Duration.ofMillis(100);

    private Waits() {
    }

    // ---------------------------------------------------------------- conditions

    /** Waits until {@code element} exists and is displayed, then returns it. */
    public static WebElement visible(IOSDriver driver, By by, Duration timeout) {
        return until(timeout, "visible " + by, () -> {
            WebElement el = firstOrNull(driver, by);
            return el != null && el.isDisplayed() ? el : null;
        });
    }

    public static WebElement visible(IOSDriver driver, By by, Duration timeout, String what) {
        return until(timeout, what + " [" + by + "]", () -> {
            WebElement el = firstOrNull(driver, by);
            return el != null && el.isDisplayed() ? el : null;
        });
    }

    public static WebElement visible(IOSDriver driver, By by) {
        return visible(driver, by, MEDIUM);
    }

    /** Waits until an already-present element reports visible and enabled. */
    public static WebElement enabledElement(IOSDriver driver, By by, Duration timeout, String what) {
        return enabledElement(driver, by, timeout, what, EVENT_POLL_INTERVAL);
    }

    /**
     * Waits for one already-visible control to become enabled, then returns that
     * same element.
     *
     * <p>The element is resolved once and only its state is polled afterward.
     * Repeating a full-tree lookup on every tick is wasteful for a control that
     * is already known to be on screen; it also means a slow lookup can delay
     * detection of the state change by more than the nominal poll interval.
     * The lookup is repeated only if the cached reference goes stale because
     * SwiftUI rebuilt the view underneath it.
     */
    public static WebElement enabledElement(
            IOSDriver driver, By by, Duration timeout, String what, Duration pollInterval) {
        WebElement[] current = new WebElement[1];
        return until(timeout, what, () -> {
            try {
                if (current[0] == null) {
                    current[0] = driver.findElement(by);
                }
                if (current[0].isDisplayed() && current[0].isEnabled()) {
                    return current[0];
                }
                return null;
            } catch (NoSuchElementException | StaleElementReferenceException transientFailure) {
                current[0] = null;
                return null;
            }
        }, pollInterval);
    }

    /** Waits until the element exists and reports {@code enabled}. */
    public static WebElement enabled(IOSDriver driver, By by, Duration timeout) {
        return enabled(driver, by, timeout, "enabled " + by);
    }

    /**
     * Waits for an enabled, visible element.
     *
     * <p>The {@code what} overload exists so a failure can name the interaction
     * that was being attempted. "enabled accessibilityId: Continue" is close to
     * useless when six consecutive steps all end in a Continue tap — the
     * interesting question is which step's Continue was missing.
     */
    public static WebElement enabled(IOSDriver driver, By by, Duration timeout, String what) {
        return until(timeout, what, () -> {
            WebElement el = firstOrNull(driver, by);
            return el != null && el.isDisplayed() && el.isEnabled() ? el : null;
        });
    }

    public static boolean isVisible(IOSDriver driver, By by, Duration timeout) {
        try {
            visible(driver, by, timeout);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Checks once whether a displayed element currently matches.
     *
     * <p>Unlike {@link #isVisible}, this never waits. It exists for conditions
     * that are themselves evaluated inside another wait loop. Calling a waiting
     * check from inside a polling loop multiplies the delays: each negative
     * evaluation would consume that check's whole timeout before the outer loop
     * could poll again.
     */
    public static boolean isPresent(IOSDriver driver, By by) {
        try {
            for (WebElement element : driver.findElements(by)) {
                try {
                    if (element.isDisplayed()) {
                        return true;
                    }
                } catch (NoSuchElementException | StaleElementReferenceException transientFailure) {
                    // The tree changed while it was being read; treat this match
                    // as absent on this pass and let the outer wait poll again.
                }
            }
            return false;
        } catch (NoSuchElementException | StaleElementReferenceException transientFailure) {
            return false;
        }
    }

    /** Waits until no visible element matches {@code by}. */
    public static void gone(IOSDriver driver, By by, Duration timeout) {
        until(timeout, "gone " + by, () -> !isVisible(driver, by, Duration.ofMillis(200)));
    }

    public static void gone(IOSDriver driver, By by) {
        gone(driver, by, MEDIUM);
    }

    /** Waits until a window title to appear on {@code other} — used to catch
     * transient system surfaces (alerts, pickers, share sheets) that Appium
     * surfaces as separate windows.
     */
    public static boolean windowAppeared(IOSDriver driver, String predicateFragment, Duration timeout) {
        return untilQuietly(timeout, () -> driver
                .findElements(com.portfolio.ios.pages.Locators.byLabelContaining(predicateFragment))
                .stream()
                .anyMatch(WebElement::isDisplayed));
    }

    // ---------------------------------------------------------------- plumbing

    @FunctionalInterface
    public interface Condition<T> {
        T get();
    }

    /**
     * Core retry loop. A {@code null} result from {@code condition} means "not
     * yet". Transient lookup errors (element not rendered yet, or detached
     * mid-animation) are treated the same way rather than as failures, because
     * on a SwiftUI screen mid-transition that is genuinely what they are.
     */
    public static <T> T until(Duration timeout, String description, Condition<T> condition) {
        return until(timeout, description, condition, POLL_INTERVAL);
    }

    /**
     * Core retry loop with an explicit poll interval.
     *
     * <p>Most waits should use the default interval. A custom interval is
     * justified only when the condition can change faster than a screen
     * transition—for example, one button becoming enabled at the end of video
     * playback.
     */
    public static <T> T until(
            Duration timeout, String description, Condition<T> condition, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);
        RuntimeException lastTransient = null;
        do {
            try {
                T result = condition.get();
                if (result != null) {
                    return result;
                }
            } catch (NoSuchElementException | StaleElementReferenceException transientFailure) {
                lastTransient = transientFailure;
            }
            poll(pollInterval);
        } while (Instant.now().isBefore(deadline));

        throw new WaitTimeout(description, timeout, lastTransient);
    }

    public static void untilTrue(Duration timeout, String description, Supplier<Boolean> condition) {
        until(timeout, description, () -> Boolean.TRUE.equals(condition.get()) ? Boolean.TRUE : null);
    }

    /** Same as {@link #until} but reports failure as {@code false} instead of throwing. */
    public static boolean untilQuietly(Duration timeout, Supplier<Boolean> condition) {
        try {
            return Boolean.TRUE.equals(until(timeout, "quiet condition", () -> condition.get() ? Boolean.TRUE : null));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Gap between condition re-evaluations.
     *
     * <p>This is a poll interval, not a blind sleep: the loop re-checks a real
     * condition on every tick and exits the moment it holds, so total wait is
     * bounded by how long the condition actually takes rather than by a
     * hard-coded guess.
     */
    private static void poll() {
        poll(POLL_INTERVAL);
    }

    private static void poll(Duration pollInterval) {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", e);
        }
    }

    /**
     * Runs {@code action} and retries once on staleness. SwiftUI re-creates view
     * identities on animation, so an element that was valid a frame ago can be
     * detached by the time we tap it.
     */
    public static void resilient(Runnable action) {
        try {
            action.run();
        } catch (StaleElementReferenceException first) {
            action.run();
        }
    }

    public static <T> T resilientGet(Supplier<T> action) {
        try {
            return action.get();
        } catch (StaleElementReferenceException first) {
            return action.get();
        }
    }

    private static WebElement firstOrNull(IOSDriver driver, By by) {
        return resilientGet(() -> driver.findElement(by));
    }

    /** Raised when a condition never came true — message names the condition. */
    public static final class WaitTimeout extends RuntimeException {
        public WaitTimeout(String description, Duration timeout, Throwable cause) {
            super("Timed out after " + timeout.toSeconds() + "s waiting for: " + description, cause);
        }
    }
}