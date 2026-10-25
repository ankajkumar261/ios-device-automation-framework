package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The fasting setup flow ({@code Features/Fasting/FastingSetupFlow.swift}):
 * two education screens and "Why you'll love it" (first time only), then
 * "Select Fasting Protocol" with its "Create New Protocol" pop-up, then the
 * "Set Your Schedule" sheet.
 *
 * <p>Verified on the simulator: the start time defaults to now; the sheet's
 * button reads "Start Fast" when that time is now on a scheduled day, else
 * "Schedule Fast" (disabled with "Select at least one day" when no weekday
 * is picked). Finishing closes every fasting screen and lands on Home.
 */
public final class FastingSetupPage extends BasePage {

    public FastingSetupPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    // ---------------------------------------------------------------- education

    /** Waits for setup's first screen and says whether it is the introduction (else the protocol picker). */
    public boolean isShowingEducation() {
        return Waits.until(Waits.MEDIUM, "fasting introduction or protocol picker", () -> {
            if (Waits.isPresent(driver, Locators.staticText("What is intermittent fasting?"))) {
                return Boolean.TRUE;
            }
            return Waits.isPresent(driver, Locators.staticText("Select Fasting Protocol")) ? Boolean.FALSE : null;
        });
    }

    /** Continue → Continue → Enable Fasting, checking each screen's heading. */
    @Step("Go through the fasting introduction")
    public FastingSetupPage completeEducation() {
        visible(Locators.staticText("What is intermittent fasting?"), Waits.MEDIUM, "education screen 1");
        tap(Locators.visibleButton("Continue"), "education screen 1");
        visible(Locators.staticText("Potential Health Benefits"), Waits.SHORT, "education screen 2");
        tap(Locators.visibleButton("Continue"), "education screen 2");
        visible(Locators.staticText("Why you'll love it"), Waits.SHORT, "'Why you'll love it'");
        tap(Locators.visibleButton("Enable Fasting"), "fasting intro");
        return waitForProtocolPicker();
    }

    // ---------------------------------------------------------------- protocol picker

    @Step("Wait for Select Fasting Protocol")
    public FastingSetupPage waitForProtocolPicker() {
        visible(Locators.staticText("Select Fasting Protocol"), Waits.MEDIUM, "'Select Fasting Protocol'");
        return this;
    }

    /** Card labels, e.g. "16, Hours, 16:8 - Classic, Fast for 16 hours …". */
    public List<String> protocolCards() {
        List<String> labels = new ArrayList<>();
        for (WebElement card : driver.findElements(Locators.fastingProtocolCards())) {
            labels.add(Waits.resilientGet(() -> card.getAttribute("label")));
        }
        return labels;
    }

    /** Picks a preset by its ratio, "16:8". The selection has no state in the tree. */
    @Step("Choose the {ratio} protocol")
    public FastingSetupPage choosePreset(String ratio) {
        tap(Locators.fastingProtocolCard(ratio + " - "), "protocol card");
        return this;
    }

    @Step("Open Add Custom")
    public FastingSetupPage openCustom() {
        tap(Locators.fastingCustomCard(), "custom protocol card");
        visible(Locators.staticText("Create New Protocol"), Waits.SHORT, "'Create New Protocol' pop-up");
        return this;
    }

    /** Hours in the custom pop-up: the text just after its question. */
    public int customHours() {
        List<String> texts = visibleTextsInOrder();
        int at = texts.indexOf("How long do you want to fast for?");
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No hours in the custom pop-up; texts: " + texts);
        }
        return digits(texts.get(at + 1));
    }

    public boolean isCustomStepEnabled(String name) {
        return Waits.resilientGet(() -> visible(Locators.visibleButtonNamed(name), Waits.SHORT, "custom " + name)
                .isEnabled());
    }

    /** Taps "+" or "−" until it turns off; returns the hours it stopped at. */
    @Step("Custom hours: {name} until it stops")
    public int customStepToLimit(String name) {
        // Each tap is checked only through the button's enabled state; the
        // value is read once at the end (a full screen read per tap was the
        // slowest part of FAST_006).
        // The button is found once and re-checked in place before each tap
        // (a fresh lookup per tap doubled the loop on the iPhone).
        WebElement button = visible(Locators.visibleButtonNamed(name), Waits.SHORT, "custom " + name);
        for (int i = 0; i < 30; i++) {
            try {
                if (!button.isEnabled()) {
                    break;
                }
                button.click();
            } catch (org.openqa.selenium.StaleElementReferenceException
                     | org.openqa.selenium.NoSuchElementException rebuilt) {
                button = visible(Locators.visibleButtonNamed(name), Waits.SHORT, "custom " + name);
            }
        }
        return customHours();
    }

    @Step("Custom hours: set {hours}")
    public FastingSetupPage setCustomHours(int hours) {
        int current = customHours();
        String button = current < hours ? "plus" : "minus";
        tapRepeatedly(Locators.visibleButtonNamed(button), Math.abs(hours - current), "custom " + button);
        for (int i = 0; i < 30 && customHours() != hours; i++) {
            customStep(customHours() < hours ? "plus" : "minus");
        }
        if (customHours() != hours) {
            throw new AssertionError("Custom hours stuck at " + customHours() + ", wanted " + hours);
        }
        return this;
    }

    @Step("Confirm the custom protocol")
    public FastingSetupPage confirmCustom() {
        tap(Locators.visibleButton("Done"), "custom pop-up");
        waitForGone(Locators.staticText("Create New Protocol"), Waits.SHORT);
        return this;
    }

    public String customCardLabel() {
        return Waits.resilientGet(() -> visible(Locators.fastingCustomCard(), Waits.SHORT, "custom card")
                .getAttribute("label"));
    }

    @Step("Continue to the schedule")
    public FastingSetupPage continueToSchedule() {
        tap(Locators.visibleButton("Continue"), "protocol picker");
        visible(Locators.staticText("Set Your Schedule"), Waits.SHORT, "'Set Your Schedule' sheet");
        return this;
    }

    /** The X on the protocol picker: leaves setup. */
    @Step("Close fasting setup")
    public void close() {
        tap(Locators.visibleButton("Close"), "fasting setup");
        waitForGone(Locators.staticText("Select Fasting Protocol"), Waits.SHORT);
    }

    // ---------------------------------------------------------------- schedule sheet

    /** Start and end of the "Fast A → B" preview. */
    public LocalTime[] fastWindow() {
        return window("Fast ");
    }

    /** Start and end of the "Eat A → B" preview. */
    public LocalTime[] eatWindow() {
        return window("Eat ");
    }

    /** "Just today", "Every day" or "Selected days". */
    @Step("Repeat: {mode}")
    public FastingSetupPage chooseRepeat(String mode) {
        tap(Locators.visibleButton(mode), "repeat");
        return this;
    }

    /** Toggles the chip of {@code day}; chips run Sunday → Saturday (en_US). */
    @Step("Toggle weekday {day}")
    public FastingSetupPage toggleWeekday(DayOfWeek day) {
        List<WebElement> chips = Waits.until(Waits.SHORT, "7 weekday chips", () -> {
            List<WebElement> found = driver.findElements(Locators.fastingWeekdayChips());
            return found.size() == 7 ? found : null;
        });
        chips.sort(Comparator.comparingInt(chip -> chip.getRect().getX()));
        Waits.resilient(chips.get(day.getValue() % 7)::click);
        return this;
    }

    public boolean showsSelectOneDay() {
        return Waits.isPresent(driver, Locators.staticText("Select at least one day"));
    }

    /** "Start Fast" or "Schedule Fast". */
    public String scheduleButtonLabel() {
        return Waits.until(Waits.SHORT, "schedule button", () -> {
            for (String label : List.of("Start Fast", "Schedule Fast")) {
                if (Waits.isPresent(driver, Locators.visibleButton(label))) {
                    return label;
                }
            }
            return null;
        });
    }

    public boolean isScheduleButtonEnabled() {
        String label = scheduleButtonLabel();
        return Waits.resilientGet(() -> driver.findElement(Locators.visibleButton(label)).isEnabled());
    }

    /**
     * Taps "Start Fast" / "Schedule Fast". Setup closes onto Home; a
     * notification permission prompt, if iOS shows one, is declined.
     */
    @Step("Save the fasting schedule")
    public HomePage save() {
        String label = scheduleButtonLabel();
        tap(Locators.visibleButton(label), "schedule sheet");
        waitForGone(Locators.staticText("Set Your Schedule"), Waits.SHORT);
        new SystemDialogPage(driver, factory).declineSystemAlertIfPresent(Waits.QUICK);
        return new HomePage(driver, factory).backAtTop();
    }

    private void customStep(String name) {
        int before = customHours();
        tap(Locators.visibleButtonNamed(name), "custom " + name);
        Waits.untilTrue(Waits.SHORT, "custom hours to change from " + before, () -> customHours() != before);
    }

    private LocalTime[] window(String prefix) {
        String line = FastingText.normalise(textOf(Locators.fastingPreview(prefix), Waits.SHORT));
        String[] parts = line.substring(prefix.length()).split("→");
        return new LocalTime[]{FastingText.parseTime(parts[0]), FastingText.parseTime(parts[1])};
    }
}
