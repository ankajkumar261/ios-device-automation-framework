package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Scanner ({@code Features/Scan/ScanView.swift}): Home's "+" → "Scan".
 *
 * <p>Verified on the simulator: the first visit asks for the camera
 * ("“Cal Care” would like to access the Camera."); with access granted but no
 * camera hardware the app covers the whole screen — mode tabs included —
 * with "Camera Not Available", whose "Choose from Library" opens the iOS
 * photo picker and whose "Close" leaves the Scanner. So on the simulator the
 * only way to a result is a library photo; Type mode needs a real camera
 * (or no overlay) to be reachable.
 *
 * <p>The iOS 26 photo picker draws its grid as one element
 * ({@code photos_sectioned_layout}) with no per-photo children, so a photo
 * can only be picked by its position in that grid.
 */
public final class ScannerPage extends BasePage {

    public static final List<String> MODES = List.of("Snap", "Barcode", "Voice", "Type", "Bill");
    public static final String CAMERA_NOT_AVAILABLE = "Camera Not Available";
    public static final String CAMERA_NOT_AVAILABLE_TEXT = "This device doesn't have a camera available.\n"
            + "You can still log food from your photo library, voice, text, or a barcode.";
    public static final String CAMERA_ACCESS_NEEDED = "Camera Access Needed";

    /** The backend reads a photo in seconds; allow for a slow production AI. */
    private static final Duration ANALYSIS = Duration.ofSeconds(90);
    private static final String PHOTO_GRID = "photos_sectioned_layout";

    public ScannerPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the Scanner")
    public ScannerPage waitUntilLoaded() {
        visible(Locators.staticText("Scanner"), Waits.MEDIUM, "'Scanner' title");
        return this;
    }

    // ---------------------------------------------------------------- camera permission

    /** Answers the camera prompt if iOS shows it; a no-op once the app already has an answer. */
    @Step("Answer the camera prompt if asked: allow={allow}")
    public ScannerPage answerCameraPromptIfAsked(boolean allow) {
        SystemAlert.answerIfShown(driver, "camera", allow, Waits.QUICK);
        return this;
    }

    // ---------------------------------------------------------------- screen

    /** Labels of the mode tabs, in order. */
    public List<String> modes() {
        List<String> modes = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeButton".equals(element[0]) && MODES.contains(element[1])) {
                modes.add(element[1]);
            }
        }
        return modes;
    }

    /** Waits for "Camera Not Available" (no camera hardware); false if the camera came up instead. */
    public boolean showsCameraNotAvailable() {
        return isVisible(Locators.staticText(CAMERA_NOT_AVAILABLE), Waits.SHORT);
    }

    public static final String CAMERA_ACCESS_TEXT = "Cal Care needs camera access to scan food and barcodes.\n"
            + "Enable it in Settings to continue.";

    public String accessNeededText() {
        return textOf(Locators.visibleTextContaining("needs camera access"), Waits.SHORT);
    }

    /** "Not Now" on "Camera Access Needed": leaves the Scanner. */
    @Step("Not now")
    public HomePage notNow() {
        tap(Locators.visibleButton("Not Now"), "camera access overlay");
        waitForGone(Locators.staticText("Scanner"), Waits.SHORT);
        return new HomePage(driver, factory).waitUntilLoaded();
    }

    public boolean showsCameraAccessNeeded() {
        return isVisible(Locators.staticText(CAMERA_ACCESS_NEEDED), Waits.SHORT);
    }

    public String overlayText() {
        return textOf(Locators.visibleTextContaining("This device doesn't have a camera"), Waits.SHORT);
    }

    public boolean hasButton(String label) {
        return Waits.isPresent(driver, Locators.visibleButton(label));
    }

    /** True when the Type tab's text field is on screen. */
    public boolean isTypeModeShowing() {
        return Waits.isPresent(driver, Locators.staticText("Type what you ate"));
    }

    @Step("Tap the {mode} tab")
    public ScannerPage tapMode(String mode) {
        tap(Locators.visibleButton(mode), "scanner mode tab");
        return this;
    }

    /** "Close" on the camera overlay: leaves the Scanner. */
    @Step("Close the camera overlay")
    public HomePage closeOverlay() {
        tap(Locators.visibleButton("Close"), "camera overlay");
        waitForGone(Locators.staticText("Scanner"), Waits.SHORT);
        return new HomePage(driver, factory).waitUntilLoaded();
    }

    /** The back arrow (top left) leaves the Scanner. */
    @Step("Leave the Scanner")
    public HomePage back() {
        tap(Locators.visibleButtonNamed("scanBack"), "scanner back");
        waitForGone(Locators.staticText("Scanner"), Waits.SHORT);
        return new HomePage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- getting a result

    /**
     * "Choose from Library" → the newest photo in the library. The picker
     * takes a few seconds to load its grid the first time.
     */
    @Step("Scan the newest photo in the library")
    public ScannerPage chooseNewestLibraryPhoto() {
        // Observed: a tap while the overlay is still settling can be lost, so
        // re-tap only if the grid has not come up after a full wait.
        WebElement grid = null;
        for (int attempt = 1; attempt <= 3 && grid == null; attempt++) {
            tap(Locators.visibleButton("Choose from Library"), "camera overlay");
            grid = photoGrid(Waits.SHORT);
        }
        if (grid == null) {
            throw new AssertionError("The photo picker did not open after 3 taps on 'Choose from Library'");
        }
        // No accessible photo cells (see class note): tap the centre of the
        // first cell of the 3-column grid, computed from the grid's own frame.
        Rectangle frame = Waits.resilientGet(grid::getRect);
        int cell = frame.getWidth() / 3;
        driver.executeScript("mobile: tap", Map.of("x", frame.getX() + cell / 2, "y", frame.getY() + cell / 2));
        Waits.untilTrue(Waits.MEDIUM, "photo picker closed", () ->
                driver.findElements(io.appium.java_client.AppiumBy.accessibilityId(PHOTO_GRID)).isEmpty());
        return this;
    }

    /** The picker's photo grid, or null if it does not appear within {@code timeout}. */
    private WebElement photoGrid(Duration timeout) {
        try {
            return Waits.until(timeout, "the photo picker grid", () -> {
                List<WebElement> found = driver.findElements(
                        io.appium.java_client.AppiumBy.accessibilityId(PHOTO_GRID));
                return found.isEmpty() ? null : found.get(0);
            });
        } catch (Waits.WaitTimeout notYet) {
            return null;
        }
    }

    /** Type tab: describes a meal in words and sends it (needs the tab to be reachable). */
    @Step("Describe the food: '{text}'")
    public ScannerPage describeFood(String text) {
        tapMode("Type");
        visible(Locators.staticText("Type what you ate"), Waits.SHORT, "Type tab");
        typeInto(visible(Locators.visibleTextField(), Waits.SHORT, "food description field"), text);
        tap(Locators.visibleButtonNamed("arrow.up.circle.fill"), "Type tab send");
        return this;
    }

    /**
     * Waits for the analysis to end: the result screen, or the error card.
     * Returns the result; fails with the card's text on an error.
     */
    @Step("Wait for the scan result")
    public ScanResultPage waitForResult() {
        String error = Waits.until(ANALYSIS, "scan result or error card", () -> {
            if (Waits.isPresent(driver, Locators.scanLogButton())) {
                return "";
            }
            if (Waits.isPresent(driver, Locators.visibleButton("Try again"))) {
                return String.join(" | ", visibleTextsInOrder());
            }
            return null;
        });
        if (!error.isEmpty()) {
            throw new AssertionError("The scan ended on an error card: " + error);
        }
        return new ScanResultPage(driver, factory).waitUntilLoaded();
    }

    /** Waits for the analysis error card and returns its texts (title, message, …). */
    @Step("Wait for the scan error card")
    public List<String> waitForErrorCard() {
        visible(Locators.visibleButton("Try again"), ANALYSIS, "scan error card's 'Try again'");
        return visibleTextsInOrder();
    }

    /** "Cancel" on the error card: back to the Scanner. */
    @Step("Cancel the scan error")
    public ScannerPage cancelError() {
        tap(Locators.visibleButton("Cancel"), "scan error card");
        waitForGone(Locators.visibleButton("Try again"), Waits.SHORT);
        return this;
    }
}
