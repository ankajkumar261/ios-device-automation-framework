package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * The alert "End Fast" opens, from Home's banner sheet and from the hub alike.
 *
 * <p>Under 4 hours: "End this short fast?" with "End & Save", "End & Discard",
 * "Keep Fasting". From 4 hours: "End this fast?" with "End Fast", "Keep Fasting".
 */
public final class FastingEndAlert extends BasePage {

    public static final String SHORT_TITLE = "End this short fast?";
    public static final String LONG_TITLE = "End this fast?";

    public FastingEndAlert(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    public FastingEndAlert waitUntilShown() {
        Waits.until(Waits.SHORT, "end-fast alert", () -> Waits.isPresent(driver, Locators.alertTitled(SHORT_TITLE))
                || Waits.isPresent(driver, Locators.alertTitled(LONG_TITLE)) ? Boolean.TRUE : null);
        return this;
    }

    public String title() {
        return Waits.isPresent(driver, Locators.alertTitled(SHORT_TITLE)) ? SHORT_TITLE : LONG_TITLE;
    }

    /** The alert's message (its second text). */
    public String message() {
        List<WebElement> texts = driver.findElements(Locators.alertTexts());
        if (texts.size() < 2) {
            throw new AssertionError("End-fast alert has no message");
        }
        return FastingText.normalise(Waits.resilientGet(() -> texts.get(1).getAttribute("label")));
    }

    public List<String> buttons() {
        Object buttons = ((JavascriptExecutor) driver).executeScript("mobile: alert",
                Map.of("action", "getButtons"));
        return buttons instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** "Keep Fasting", "End & Save", "End & Discard" or "End Fast". */
    @Step("End-fast alert: {button}")
    public void choose(String button) {
        String shown = title();
        tap(Locators.alertActionButton(button), "end-fast alert");
        waitForGone(Locators.alertTitled(shown), Waits.SHORT);
        // Opened from Home's banner, the alert sits over the banner's detail sheet
        // ("Fasting Started" row); the hub has no such sheet, so this is a no-op there.
        // Close the sheet so Home is usable again — whether the fast was kept,
        // saved or discarded, the test continues on Home.
        if (Waits.isPresent(driver, Locators.staticText("Fasting Started"))) {
            tap(Locators.visibleButton("Close"), "fasting sheet");
            waitForGone(Locators.staticText("Fasting Started"), Waits.SHORT);
        }
    }
}
