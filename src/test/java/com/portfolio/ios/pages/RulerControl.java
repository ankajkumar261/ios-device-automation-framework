package com.portfolio.ios.pages;

import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drives the app's {@code RulerScalePicker} — a SwiftUI {@code Canvas} with no
 * accessibility children — by synthetic drags, reading the number above it
 * after each one until it lands on target. Used by onboarding steps 17-19
 * ({@link BodyMetricsPage}) and the Reset Goals height / weight steps, which
 * use the same control. See {@link BodyMetricsPage} for the full rationale.
 */
public final class RulerControl {

    /** Horizontal distance between ruler ticks, from the app's default. */
    private static final double TICKS_SPACING_PT = 8.0;
    /** Safety margin so a convergence drag never aims at a screen edge. */
    private static final int MAX_DRAG_PT = 170;

    /** Offsets below the readout to try, in order, as the ruler hit point. */
    private static final int[] RULER_HIT_OFFSETS = {60, 80, 40, 100, 20};

    /**
     * Maximum ruler adjustments for one measurement.
     *
     * <p>Thirty is generous enough for a large move from the app's default while
     * still failing promptly when the ruler stops responding.
     */
    private static final int MAX_ITERATIONS = 30;
    /** Time allowed for the ruler's release/snap animation to settle after a drag. */
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(3);

    private final IOSDriver driver;

    public RulerControl(IOSDriver driver) {
        this.driver = driver;
    }

    /**
     * Moves the current step's ruler to {@code target} and verifies it landed.
     *
     * <p>Each iteration re-reads the live value rather than trusting the previous
     * iteration's arithmetic, so the loop converges even when a single drag does
     * not move exactly as predicted. The caller supplies the value represented by
     * one minor tick because height uses whole units while the weight rulers use
     * tenths; using height's geometry for weight underestimates each drag by a
     * factor of ten.
     */
    public void drive(double target, double unitsPerTick, String what) {
        Supplier<WebElement> readout = () -> Waits.until(Waits.MEDIUM,
                what + " numeric readout", this::findNumericReadout);
        double tolerance = Math.max(0.06, unitsPerTick / 2);
        double current = readValue(readout.get());

        for (int attempt = 0; attempt < MAX_ITERATIONS && Math.abs(target - current) > tolerance; attempt++) {
            double delta = target - current;
            // value += (-dx / spacing) * unitsPerTick, so dx is negative/leftward
            // when the target is larger than the current value.
            int dx = (int) Math.round(-delta * TICKS_SPACING_PT / unitsPerTick);
            dx = (int) Math.max(-MAX_DRAG_PT, Math.min(MAX_DRAG_PT, dx));

            double before = current;
            current = dragAndSettle(readout, dx, before, what);

            if (Math.abs(current - before) < 0.05) {
                throw new AssertionError("Ruler for " + what + " stopped responding at "
                        + trim(before) + " while trying to reach " + trim(target)
                        + ". The drag is not registering on the ruler — check that the hit "
                        + "point below the readout still lands inside the ruler's 112pt frame.");
            }
        }

        if (Math.abs(target - current) > tolerance) {
            throw new AssertionError("Ruler for " + what + " settled at " + trim(current)
                    + " instead of " + trim(target) + " after " + MAX_ITERATIONS + " drags.");
        }
    }

    /**
     * Issues one drag and reads the resulting settled value.
     *
     * <p>If the value did not move, the next hit offset in
     * {@link #RULER_HIT_OFFSETS} is tried before giving up, because the ruler's
     * vertical position depends on the step's header and toggle heights and
     * shifts between steps 17, 18 and 19.
     */
    private double dragAndSettle(Supplier<WebElement> readout, int dx, double before, String what) {
        for (int offsetY : RULER_HIT_OFFSETS) {
            WebElement currentReadout = readout.get();
            Rectangle box = Waits.resilientGet(currentReadout::getRect);
            int startX = box.getX() + box.getWidth() / 2;
            Gestures.dragAt(driver, startX, box.getY() + box.getHeight() + offsetY, dx, 0);
            double after = readSettledValue(readout);
            if (Math.abs(after - before) > 0.05) {
                return after;
            }
        }
        return before;
    }

    /**
     * Reads the readout after its post-drag snap animation has finished.
     *
     * <p>The ruler snaps to the nearest tick on release, so checking immediately
     * can catch a transitional value. This polls until two consecutive reads agree,
     * with a bounded timeout; it never sleeps blindly.
     */
    private double readSettledValue(Supplier<WebElement> readout) {
        double[] latest = {readValue(readout.get())};
        Waits.untilQuietly(SETTLE_TIMEOUT, () -> {
            double next = readValue(readout.get());
            boolean stable = Math.abs(next - latest[0]) < 0.03;
            latest[0] = next;
            return stable;
        });
        return latest[0];
    }

    /**
     * The large value above the ruler.
     *
     * <p>Identified by content: on steps 17-19 it is the only
     * {@code StaticText} whose label is a bare number. The ruler's own tick
     * labels are drawn into the {@code Canvas} and never enter the
     * accessibility tree, so there is nothing to confuse it with.
     *
     * <p>Values are parsed metric-only by construction — the suite stays in
     * cm/kg, so the feet display form ("5'11\"") is not expected here.
     */
    private WebElement findNumericReadout() {
        List<WebElement> candidates = driver.findElements(Locators.numericReadout());
        for (WebElement candidate : candidates) {
            if (!candidate.isDisplayed()) {
                continue;
            }
            if (NUMERIC.matcher(textOf(candidate)).matches()) {
                return candidate;
            }
        }
        return null;
    }

    private static final Pattern NUMERIC = Pattern.compile("\\d{1,4}(\\.\\d+)?");

    private double readValue(WebElement readout) {
        String text = textOf(readout);
        Matcher matcher = NUMERIC.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError(
                    "Ruler readout shows '" + text + "', which is not a number. "
                            + "The step is probably not a ruler step, or the unit toggle is on lb/ft.");
        }
        return Double.parseDouble(matcher.group());
    }

    private String textOf(WebElement element) {
        return Waits.resilientGet(() -> {
            String text = element.getText();
            if (text != null && !text.isBlank()) {
                return text;
            }
            // No executeScript fallback: XCUITest does not implement it, so the
            // attribute read has to be the primary and only route.
            String value = element.getAttribute("value");
            return value == null ? "" : value;
        });
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
