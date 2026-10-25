package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The full-screen "Live Workout" timer ({@code Features/Exercise/LiveWorkoutView.swift}).
 *
 * <p>Verified on the simulator: activity chips ("Walking", "Running", …),
 * the clock "00:00", "Start Workout" (then "End Workout"), and "Go Down" to
 * close while idle. Ending opens the alert "End workout?": under 10 s it
 * offers only "Discard" / "Keep going"; from 10 s "Save workout" too.
 */
public final class LiveWorkoutPage extends BasePage {

    public static final String END_ALERT = "End workout?";
    public static final String TOO_SHORT =
            "This workout is too short to save — give it at least 10 seconds.";
    public static final String UNDER_A_MINUTE = "This workout is under a minute. Save it anyway?";

    public LiveWorkoutPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the live workout screen")
    public LiveWorkoutPage waitUntilLoaded() {
        visible(Locators.staticText("Live Workout"), Waits.MEDIUM, "'Live Workout' screen");
        return this;
    }

    @Step("Choose {type}")
    public LiveWorkoutPage chooseType(String type) {
        tap(Locators.visibleButton(type), "live workout activity chip");
        return this;
    }

    @Step("Start the workout")
    public LiveWorkoutPage start() {
        tap(Locators.visibleButton("Start Workout"), "live workout");
        visible(Locators.visibleButton("End Workout"), Waits.SHORT, "'End Workout' once running");
        return this;
    }

    /** Seconds on the clock ("00:12" → 12). */
    public int elapsedSeconds() {
        String[] parts = textOf(Locators.liveWorkoutTimer(), Waits.SHORT).split(":");
        int seconds = 0;
        for (String part : parts) {
            seconds = seconds * 60 + Integer.parseInt(part);
        }
        return seconds;
    }

    /** Waits for the running clock to reach {@code seconds} (the clock is the state). */
    @Step("Let the clock reach {seconds} s")
    public LiveWorkoutPage waitForClock(int seconds) {
        Waits.untilTrue(Duration.ofSeconds(seconds + 15L), "live clock to reach " + seconds + " s",
                () -> elapsedSeconds() >= seconds);
        return this;
    }

    @Step("End the workout")
    public LiveWorkoutPage end() {
        tap(Locators.visibleButton("End Workout"), "live workout");
        visible(Locators.alertTitled(END_ALERT), Waits.SHORT, "'" + END_ALERT + "' alert");
        return this;
    }

    public boolean endAlertSays(String message) {
        return Waits.isPresent(driver, Locators.staticText(message));
    }

    /** Buttons of the on-screen alert, as iOS reports them. */
    public List<String> endAlertButtons() {
        Object buttons = ((JavascriptExecutor) driver).executeScript("mobile: alert",
                Map.of("action", "getButtons"));
        return buttons instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    /** Saves and closes the screen; it returns to wherever it was opened from. */
    @Step("Save the workout")
    public void saveWorkout() {
        closeWith("Save workout");
    }

    /** Discards and closes the screen; it returns to wherever it was opened from. */
    @Step("Discard the workout")
    public void discard() {
        closeWith("Discard");
    }

    /**
     * Cleanup for a test that stopped part-way: discards a running workout or
     * closes the idle screen, so the next test starts clean.
     */
    public void abandon() {
        try {
            if (!Waits.isPresent(driver, Locators.staticText("Live Workout"))) {
                return;
            }
            if (!Waits.isPresent(driver, Locators.alertTitled(END_ALERT))
                    && Waits.isPresent(driver, Locators.visibleButton("End Workout"))) {
                end();
            }
            if (Waits.isPresent(driver, Locators.alertTitled(END_ALERT))) {
                discard();
            } else if (Waits.isPresent(driver, Locators.visibleButton("Go Down"))) {
                tap(Locators.visibleButton("Go Down"), "live workout");
                waitForGone(Locators.staticText("Live Workout"), Waits.SHORT);
            }
        } catch (WebDriverException | AssertionError cleanupFailure) {
            // Best effort: the next test's Home reset closes what is left.
        }
    }

    private void closeWith(String button) {
        tap(Locators.alertActionButton(button), "'" + END_ALERT + "' alert");
        waitForGone(Locators.staticText("Live Workout"), Waits.SHORT);
    }
}
