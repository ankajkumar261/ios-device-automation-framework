package com.portfolio.ios.pages;

import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;

import java.time.Duration;
import java.util.Map;

/**
 * iOS permission prompts ("“Cal Care” would like to access the Camera.",
 * "… Would Like to Send You Notifications", "… Speech Recognition"). They
 * belong to the system, not the app, so they are read and answered through
 * the alert API — which also sidesteps label matching: iOS 26 spells the
 * decline button "Don’t Allow" with a curly apostrophe.
 */
public final class SystemAlert {

    private SystemAlert() {
    }

    /** The prompt's text if one containing {@code fragment} shows within {@code within}, else null. */
    public static String waitFor(IOSDriver driver, String fragment, Duration within) {
        String[] seen = new String[1];
        boolean showing = Waits.untilQuietly(within, () -> {
            String text = textNow(driver);
            // Wording changes between iOS releases ("… would like to access the
            // Camera." on iOS 26, "Allow … to access your camera?" on iOS 27):
            // match a key word, ignoring case.
            if (text != null && text.toLowerCase(java.util.Locale.ROOT)
                    .contains(fragment.toLowerCase(java.util.Locale.ROOT))) {
                seen[0] = text;
                return true;
            }
            return false;
        });
        return showing ? seen[0] : null;
    }

    /**
     * Taps Allow ({@code true}) or Don't Allow ({@code false}) on the prompt
     * whose text is {@code prompt}, and waits for that prompt to go. The app
     * may show its own alert straight after, so only this prompt is awaited.
     */
    public static void answer(IOSDriver driver, String prompt, boolean allow) {
        driver.executeScript("mobile: alert", Map.of("action", allow ? "accept" : "dismiss"));
        Waits.untilTrue(Waits.SHORT, "permission prompt gone", () -> !prompt.equals(textNow(driver)));
    }

    /** Answers the prompt containing {@code fragment} if it shows within {@code within}; true if it did. */
    public static boolean answerIfShown(IOSDriver driver, String fragment, boolean allow, Duration within) {
        String prompt = waitFor(driver, fragment, within);
        if (prompt == null) {
            return false;
        }
        answer(driver, prompt, allow);
        return true;
    }

    private static String textNow(IOSDriver driver) {
        try {
            return driver.switchTo().alert().getText();
        } catch (RuntimeException noAlert) {
            return null;
        }
    }
}
