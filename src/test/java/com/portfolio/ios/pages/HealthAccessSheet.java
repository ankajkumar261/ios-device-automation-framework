package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Gestures;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;


/**
 * The Apple Health sheet ({@code Features/Home/HealthConnectSheet.swift}),
 * shown from Profile when Apple Health access is turned down. Once declined
 * it reads "Apple Health Access Denied" with how to turn access on in
 * Settings, and has no button: iOS will not ask again. It is a 500 pt sheet,
 * closed by tapping the dimmed area above it.
 */
public final class HealthAccessSheet extends BasePage {

    public static final String DENIED_TITLE = "Apple Health Access Denied";
    public static final String DENIED_TEXT = "iOS won't ask again. Open the Settings app and go to Privacy & "
            + "Security → Health → Cal Care, then turn on the permissions to use Apple Health here.";

    public HealthAccessSheet(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /** Waits for the sheet in its "denied" state. */
    @Step("Wait for 'Apple Health Access Denied'")
    public HealthAccessSheet waitUntilDenied() {
        visible(Locators.staticText(DENIED_TITLE), Waits.MEDIUM, "'" + DENIED_TITLE + "' sheet");
        return this;
    }

    public boolean showsDeniedText() {
        return Waits.isPresent(driver, Locators.staticText(DENIED_TEXT));
    }

    /** The denied sheet offers no Continue / connect button. */
    public boolean hasContinue() {
        return Waits.isPresent(driver, Locators.visibleButton("Continue"));
    }

    /**
     * Taps the dimmed area half-way above the sheet. The sheet has no grabber
     * or close button; its top is the highest tall container under the
     * window (verified: y=353, 513 pt high on an iPhone 17 Pro).
     */
    @Step("Close the Apple Health sheet")
    public ProfilePage close() {
        int windowHeight = driver.manage().window().getSize().getHeight();
        int windowWidth = driver.manage().window().getSize().getWidth();
        int sheetTop = windowHeight;
        for (WebElement other : driver.findElements(Locators.visibleOfType("XCUIElementTypeOther"))) {
            Rectangle frame = Waits.resilientGet(other::getRect);
            if (frame.getY() > 0 && frame.getHeight() >= 300 && frame.getHeight() < windowHeight) {
                sheetTop = Math.min(sheetTop, frame.getY());
            }
        }
        if (sheetTop >= windowHeight) {
            throw new AssertionError("Could not find the top of the Apple Health sheet");
        }
        Gestures.tapAt(driver, windowWidth / 2, sheetTop / 2);
        waitForGone(Locators.staticText(DENIED_TITLE), Waits.SHORT);
        return new ProfilePage(driver, factory).waitUntilLoaded();
    }
}
