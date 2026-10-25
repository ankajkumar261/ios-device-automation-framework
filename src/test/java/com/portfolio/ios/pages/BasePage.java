package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;

/**
 * Shared behaviour for every page object: waiting, tapping and the three
 * gestures this app cannot be driven with a plain {@code tap()} — press-and-hold,
 * drag, and picker-wheel selection.
 *
 * <p>Subclasses expose <i>intent</i> ({@code selectGender("Male")}); they never
 * build a {@code By} inline. All selectors live in {@link Locators}.
 */
public abstract class BasePage {

    protected final IOSDriver driver;
    protected final DriverFactory factory;

    protected BasePage(IOSDriver driver, DriverFactory factory) {
        this.driver = driver;
        this.factory = factory;
    }

    // ---------------------------------------------------------------- lookup

    protected WebElement visible(By by) {
        return visible(by, Waits.MEDIUM);
    }

    protected WebElement visible(By by, Duration timeout) {
        return Waits.visible(driver, by, timeout);
    }

    protected WebElement visible(By by, Duration timeout, String what) {
        return Waits.visible(driver, by, timeout, what);
    }

    protected boolean isVisible(By by, Duration timeout) {
        return Waits.isVisible(driver, by, timeout);
    }

    protected void waitForGone(By by, Duration timeout) {
        Waits.gone(driver, by, timeout);
    }

    protected String textOf(By by, Duration timeout) {
        return Waits.resilientGet(() -> visible(by, timeout).getText());
    }

    /** Every visible match, for screens where the same label repeats. */
    protected List<WebElement> all(By by) {
        return driver.findElements(by);
    }

    // ---------------------------------------------------------------- tapping

    @Step("Tap {0}")
    protected void tap(By by) {
        tap(by, null);
    }

    /**
     * Taps {@code by}, optionally naming the interaction for failure messages.
     *
     * <p>Waits for the element to be enabled as well as present: a Continue
     * button that is present but disabled produces the same end state as a
     * missing one, so distinguishing them at the tap is what makes the two
     * diagnosable from the message alone.
     */
    protected void tap(By by, String context) {
        tapWhenEnabled(by, Waits.MEDIUM, context);
    }

    /**
     * Waits once for an element to become visible and enabled, then clicks the
     * same element reference immediately.
     *
     * <p>This avoids the slower two-phase pattern of waiting for visibility and
     * then performing a second lookup/wait for enabled state. For an event-driven
     * control—such as a video CTA that becomes enabled when playback
     * finishes—the second lookup can add a full poll cycle or more after the UI
     * is already ready.
     */
    protected WebElement tapWhenEnabled(By by, Duration timeout, String context) {
        WebElement element = context == null
                ? Waits.enabled(driver, by, timeout)
                : Waits.enabled(driver, by, timeout, context + " — tap " + by);
        Waits.resilient(element::click);
        return element;
    }

    /**
     * Taps one control {@code times} times — a stepper's "+" or "−" — finding
     * it once. On the iPhone a lookup costs about twice the tap itself (0.45 s
     * vs 0.3 s, measured), so looking it up before every tap doubled the loop.
     * Finds it again only if the screen rebuilt it.
     */
    protected void tapRepeatedly(By by, int times, String context) {
        if (times <= 0) {
            return;
        }
        WebElement element = tapWhenEnabled(by, Waits.MEDIUM, context);
        for (int i = 1; i < times; i++) {
            try {
                element.click();
            } catch (org.openqa.selenium.StaleElementReferenceException
                     | org.openqa.selenium.NoSuchElementException rebuilt) {
                element = tapWhenEnabled(by, Waits.MEDIUM, context);
            }
        }
    }

    @Step("Tap \"{label}\"")
    protected void tapLabel(String label) {
        tap(Locators.byExactLabel(label));
    }

    @Step("Tap the element at index {index} of \"{label}\"")
    protected void tapNthLabel(String label, int index) {
        WebElement el = Waits.resilientGet(() -> {
            List<WebElement> matches = all(Locators.byExactLabel(label));
            if (matches.size() <= index) {
                throw new Waits.WaitTimeout(label + " x" + (index + 1),
                        Waits.SHORT, new IllegalStateException("only " + matches.size() + " present"));
            }
            return matches.get(index);
        });
        Waits.resilient(el::click);
    }

    // ---------------------------------------------------------------- gestures

    /**
     * Press and hold an element for {@code hold}.
     *
     * <p>Step 24 ({@code tap_and_hold}) will not advance on a normal tap: it is
     * wired to {@code DragGesture(minimumDistance: 0)} and only commits after a
     * sustained 1.3s hold. The app cancels the sequence if the finger lifts
     * early, so pass a duration comfortably longer than its own threshold.
     */
    @Step("Press and hold for {millis}ms")
    protected void pressAndHold(WebElement element, Duration hold) {
        Gestures.pressAndHold(driver, element, hold);
    }

    /**
     * Drag from the element's centre by {@code (dx, dy)} points.
     *
     * <p>Used for the app's custom {@code RulerScalePicker}, which is a SwiftUI
     * {@code Canvas}: it exposes no slider, no picker and no child elements, so a
     * synthetic drag is the only way to move it.
     */
    @Step("Drag by ({dx}, {dy})")
    protected void drag(WebElement element, int dx, int dy) {
        Gestures.drag(driver, element, dx, dy);
    }

    /** Select a value on a {@code XCUIElementTypePickerWheel} (steps 7 and 9). */
    @Step("Set picker wheel to \"{value}\"")
    /**
     * Sets a wheel value.
     *
     * <p>Takes a re-resolving locator rather than an element because SwiftUI
     * rebuilds a picker's hosting views on every selection change, which makes
     * any captured element reference stale mid-loop.
     */
    protected void setWheelValue(java.util.function.Supplier<WebElement> wheel, String value) {
        Gestures.setPickerWheelValue(driver, wheel, value);
    }

    /**
     * Sets a wheel value using a caller-supplied ordering.
     *
     * <p>Required for wheels whose values are not plain integers — see
     * {@link com.portfolio.ios.core.WheelValues}.
     */
    protected void setWheelValue(java.util.function.Supplier<WebElement> wheel, String value,
                                 java.util.function.ToIntFunction<String> sortKey) {
        Gestures.setPickerWheelValue(driver, wheel, value, sortKey);
    }

    /**
     * A re-resolving handle on the {@code index}-th picker wheel on screen.
     *
     * <p>Waits for the wheel to be present rather than indexing the list
     * directly. The eating-window step renders two wheels, and while the first
     * one's selection changes SwiftUI rebuilds the step's views — during which
     * the tree transiently exposes only one picker, so a bare
     * {@code get(index)} throws {@code IndexOutOfBoundsException} mid-loop.
     */
    protected java.util.function.Supplier<WebElement> wheelAt(int index) {
        return () -> Waits.until(Waits.SHORT, "picker wheel at index " + index, () -> {
            java.util.List<WebElement> wheels = driver.findElements(Locators.pickerWheel());
            if (wheels.size() <= index) {
                return null;
            }
            WebElement wheel = wheels.get(index);
            return wheel.isDisplayed() ? wheel : null;
        });
    }

    // ---------------------------------------------------------------- scrolling and appearance

    /**
     * Scrolls the screen's visible scroll view until {@code by} is displayed —
     * down first, then back up — and returns it.
     */
    protected WebElement scrollIntoView(By by, String what) {
        for (String direction : List.of("down", "up")) {
            for (int i = 0; i <= 12; i++) {
                if (Waits.isPresent(driver, by)) {
                    return driver.findElement(by);
                }
                if (!scrollOnce(direction)) {
                    break;
                }
            }
        }
        throw new AssertionError(what + " never scrolled into view");
    }

    /** Half a page of the first displayed scroll view; false if none or at its edge. */
    protected boolean scrollOnce(String direction) {
        for (WebElement view : driver.findElements(Locators.scrollView())) {
            if (Waits.resilientGet(view::isDisplayed)) {
                try {
                    Gestures.scroll(driver, view, direction, 0.5);
                    return true;
                } catch (RuntimeException atEdge) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Mean brightness (0-255) of the middle three-fifths of a screenshot. The
     * app's theme is not exposed in the accessibility tree; its colours are
     * unmistakable — about 20 in Dark, about 230 in Light.
     */
    public int screenBrightness() {
        try {
            java.awt.image.BufferedImage shot = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
                    driver.getScreenshotAs(org.openqa.selenium.OutputType.BYTES)));
            long sum = 0;
            int samples = 0;
            for (int y = shot.getHeight() / 5; y < shot.getHeight() * 4 / 5; y += 7) {
                for (int x = 0; x < shot.getWidth(); x += 7) {
                    int rgb = shot.getRGB(x, y);
                    sum += (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
                    samples++;
                }
            }
            return (int) (sum / samples);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read a screenshot", e);
        }
    }

    public boolean looksDark() {
        return screenBrightness() < 128;
    }

    // ---------------------------------------------------------------- input

    @Step("Type \"{text}\"")
    protected void typeInto(WebElement field, String text) {
        Waits.resilient(field::click);
        Waits.resilient(() -> field.sendKeys(text));
    }

    protected void hideKeyboard() {
        try {
            driver.hideKeyboard();
        } catch (RuntimeException ignored) {
            // No keyboard on screen — nothing to dismiss.
        }
    }

    // ---------------------------------------------------------------- reading the tree in order

    /**
     * {type, label, visible} of every element in document order, from one
     * page-source read. {@code visible} is "true" or "false" — needed where a
     * full-screen view covers Home, whose texts stay in the tree underneath.
     *
     * <p>For values the app renders as unlabeled texts beside their caption —
     * the number before "kcal", the grams after "Protein" — document order is
     * what ties the value to its caption. One request instead of one per
     * element, and no XPath.
     */
    protected List<String[]> elementsInOrder() {
        try {
            javax.xml.parsers.DocumentBuilderFactory xml = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            xml.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.NodeList nodes = xml.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(
                            driver.getPageSource().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .getElementsByTagName("*");
            List<String[]> elements = new java.util.ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);
                String label = node.getAttribute("label");
                elements.add(new String[]{node.getTagName(),
                        label.isEmpty() ? node.getAttribute("name") : label,
                        node.getAttribute("visible")});
            }
            return elements;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the page source", e);
        }
    }

    /**
     * Bounds of the first {@code type} element labelled {@code label}, read
     * from one page-source snapshot, or null if absent. For elements whose
     * live references go stale as they render — a context menu's items —
     * a snapshot has nothing to go stale.
     */
    protected org.openqa.selenium.Rectangle boundsInSource(String type, String label) {
        try {
            javax.xml.parsers.DocumentBuilderFactory xml = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            xml.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.NodeList nodes = xml.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(
                            driver.getPageSource().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .getElementsByTagName(type);
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);
                if (label.equals(node.getAttribute("label"))) {
                    return new org.openqa.selenium.Rectangle(Integer.parseInt(node.getAttribute("x")),
                            Integer.parseInt(node.getAttribute("y")), Integer.parseInt(node.getAttribute("height")),
                            Integer.parseInt(node.getAttribute("width")));
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the page source", e);
        }
    }

    /** Labels of the visible StaticTexts, in document order. */
    protected List<String> visibleTextsInOrder() {
        List<String> texts = new java.util.ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0]) && "true".equals(element[2])) {
                texts.add(element[1]);
            }
        }
        return texts;
    }

    /** "2,234" → 2234, "Goal 2234 kcal" → 2234, "/167g" → 167, "C:27g" → 27. */
    protected static int digits(String text) {
        String only = text.replaceAll("[^0-9]", "");
        if (only.isEmpty()) {
            throw new AssertionError("Expected a number in '" + text + "'");
        }
        return Integer.parseInt(only);
    }

    // ---------------------------------------------------------------- assertions

    protected void assertVisible(String description, By by, Duration timeout) {
        Waits.visible(driver, by, timeout, description);
    }

    protected void assertNotVisible(String description, By by, Duration timeout) {
        if (isVisible(by, timeout)) {
            throw new AssertionError("Expected " + description + " to NOT be visible, but it was. "
                    + "Locator: " + by);
        }
    }
}