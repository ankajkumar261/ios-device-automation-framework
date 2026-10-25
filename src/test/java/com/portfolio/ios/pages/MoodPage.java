package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The "Mood & Energy" sheet opened from Home's MOOD tile
 * ({@code Features/Home/MoodLogView.swift}).
 *
 * <p>Verified on the simulator: moods are Buttons "😢, Terrible", "😔, Bad",
 * "😐, Okay", "🙂, Good", "😄, Great"; energies "🪫, Exhausted", "😴, Low",
 * "⚡, Normal", "💪, High", "🔥, Energized"; then "Log Mood" and "Done" (closes
 * without saving). The chosen option is shown by size and colour only, so
 * results are checked on the MOOD tile.
 */
public final class MoodPage extends BasePage {

    /** Recent rows show the day like "Mon, Oct 5". */
    private static final DateTimeFormatter RECENT_DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);

    public MoodPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for the mood sheet")
    public MoodPage waitUntilLoaded() {
        visible(Locators.staticText("Energy Level"), Waits.MEDIUM, "mood sheet 'Energy Level'");
        return this;
    }

    /** Picks a mood ("Good") or an energy level ("High") by its word. */
    @Step("Choose {word}")
    public MoodPage choose(String word) {
        tap(Locators.moodOption(word), "mood sheet option");
        return this;
    }

    /** Logs the entry; the button shows "Logged!" briefly, then is ready again. */
    @Step("Log Mood")
    public MoodPage log() {
        tap(Locators.visibleButton("Log Mood"), "mood sheet");
        visible(Locators.byExactLabel("Logged!"), Waits.SHORT, "'Logged!' confirmation");
        Waits.untilTrue(Waits.SHORT, "'Log Mood' ready again",
                () -> Waits.isPresent(driver, Locators.visibleButton("Log Mood"))
                        && driver.findElement(Locators.visibleButton("Log Mood")).isEnabled());
        return this;
    }

    /** Rows in the sheet's Recent list dated {@code day}. */
    public int recentEntriesOn(LocalDate day) {
        return (int) visibleTextsInOrder().stream().filter(day.format(RECENT_DAY)::equals).count();
    }

    /** "Done" closes the sheet; it does not save. */
    @Step("Close the mood sheet")
    public HomePage done() {
        tap(Locators.visibleButton("Done"), "mood sheet");
        waitForGone(Locators.staticText("Energy Level"), Waits.SHORT);
        return new HomePage(driver, factory);
    }
}
