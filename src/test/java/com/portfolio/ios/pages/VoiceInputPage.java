package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * "Voice Input" ({@code Features/Scan/VoiceInputView.swift}): Home "+" →
 * "Voice". The mic button asks iOS for speech recognition, then the
 * microphone; if either is turned down the app shows its own alert,
 * "Microphone Access Needed", with "Open Settings" and "Cancel".
 */
public final class VoiceInputPage extends BasePage {

    public static final String ACCESS_NEEDED = "Microphone Access Needed";
    public static final String ACCESS_TEXT = "Voice logging needs microphone and speech-recognition access. "
            + "Enable both in Settings to use voice input.";

    public VoiceInputPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    public VoiceInputPage waitUntilLoaded() {
        visible(Locators.staticText("Voice Input"), Waits.MEDIUM, "'Voice Input' title");
        return this;
    }

    @Step("Tap the mic")
    public VoiceInputPage tapMic() {
        tap(Locators.visibleButtonNamed("mic.fill"), "Voice Input mic");
        return this;
    }

    /** Waits for the app's "Microphone Access Needed" alert; returns its texts. */
    public List<String> waitForAccessNeeded() {
        visible(Locators.alertTitled(ACCESS_NEEDED), Waits.MEDIUM, "'" + ACCESS_NEEDED + "' alert");
        List<String> texts = new ArrayList<>();
        for (WebElement text : driver.findElements(Locators.alertTexts())) {
            texts.add(Waits.resilientGet(() -> text.getAttribute("label")));
        }
        return texts;
    }

    public boolean alertHasButton(String label) {
        return Waits.isPresent(driver, Locators.visibleButton(label));
    }

    @Step("Cancel the alert")
    public VoiceInputPage cancelAlert() {
        tap(Locators.visibleButton("Cancel"), "'" + ACCESS_NEEDED + "' alert");
        waitForGone(Locators.alertTitled(ACCESS_NEEDED), Waits.SHORT);
        return this;
    }

    @Step("Leave Voice Input")
    public HomePage cancel() {
        tap(Locators.visibleButton("Cancel"), "Voice Input");
        waitForGone(Locators.staticText("Voice Input"), Waits.SHORT);
        return new HomePage(driver, factory).waitUntilLoaded();
    }
}
