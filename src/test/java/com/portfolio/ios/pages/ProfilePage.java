package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The Profile tab ({@code Features/Profile/ProfileView.swift}).
 *
 * <p>Verified on the simulator, texts in tree order: "About You", "Age",
 * "32 yrs", "Sex", "Female"; "Body Metrics", "170", "cm", "70.0", "kg",
 * "HEIGHT", "WEIGHT", "BMI", "24.2", "Normal"; "Diet Preferences", "Omnivore";
 * "Daily Goals", "Calories", "2234 kcal", "Protein", "167g", …; "My Goal",
 * "Maintain Weight", its tagline, "Target weight", "−5.0 kg", "65.0 kg",
 * "Weekly pace", "Maintain", "Activity", "Moderately Active"; "Daily Routine",
 * "Eating window", "8 AM – 8 PM", "COMMITMENTS", …; "Integrations" with the
 * Apple Health switch; then the "Notifications" and "Widgets" rows. Most cards
 * sit below the fold, so values are read from the whole tree.
 */
public final class ProfilePage extends BasePage {

    public ProfilePage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Open the Profile tab")
    public ProfilePage openTab() {
        new HomePage(driver, factory).openTab("Profile");
        return waitUntilLoaded();
    }

    @Step("Wait for Profile")
    public ProfilePage waitUntilLoaded() {
        // In the tree rather than visible: Profile may be scrolled past "About You".
        Waits.until(Waits.MEDIUM, "Profile 'About You'",
                () -> driver.findElements(Locators.staticText("About You")).isEmpty() ? null : Boolean.TRUE);
        return this;
    }

    /** The {@code offset}-th text after {@code caption}, anywhere on the screen. */
    public String textAfter(String caption, int offset) {
        List<String> texts = allTexts();
        int at = texts.indexOf(caption);
        if (at < 0 || at + offset >= texts.size()) {
            throw new AssertionError("No text " + offset + " after '" + caption + "' on Profile; texts: " + texts);
        }
        return texts.get(at + offset);
    }

    /** The text just before {@code unit} ("170" before "cm"). */
    public String valueBefore(String unit) {
        List<String> texts = allTexts();
        int at = texts.indexOf(unit);
        if (at < 1) {
            throw new AssertionError("No value before '" + unit + "' on Profile; texts: " + texts);
        }
        return texts.get(at - 1);
    }

    /** Commitments listed under "COMMITMENTS", up to the next card. */
    public List<String> commitments() {
        List<String> texts = allTexts();
        List<String> items = new ArrayList<>();
        for (int i = texts.indexOf("COMMITMENTS") + 1; i > 0 && i < texts.size(); i++) {
            if (List.of("Integrations", "Health Concerns").contains(texts.get(i))) {
                break;
            }
            items.add(texts.get(i));
        }
        return items;
    }

    public boolean isAppleHealthOn() {
        return "1".equals(Waits.resilientGet(() -> driver.findElement(Locators.appleHealthSwitch())
                .getAttribute("value")));
    }

    /** "Connected" or "Not connected" — the text under "Apple Health". */
    public String appleHealthStatus() {
        return textAfter("Apple Health", 1);
    }

    /** Taps the Apple Health switch itself (its row's centre is the label, which ignores taps). */
    @Step("Tap the Apple Health switch")
    public ProfilePage tapAppleHealth() {
        scrollIntoView(Locators.appleHealthSwitch(), "Apple Health switch");
        tap(Locators.appleHealthSwitchControl(), "Apple Health switch");
        return this;
    }

    /** Waits for the Apple Health switch to show {@code on}. */
    public ProfilePage waitForAppleHealth(boolean on) {
        Waits.untilTrue(Waits.MEDIUM, "Apple Health switch " + (on ? "on" : "off"), () -> isAppleHealthOn() == on);
        return this;
    }

    @Step("Open Notifications")
    public NotificationSettingsPage openNotifications() {
        WebElement row = scrollIntoView(Locators.visibleButton("Notifications"), "Notifications row");
        Waits.resilient(row::click);
        return new NotificationSettingsPage(driver, factory).waitUntilLoaded();
    }

    @Step("Open Widgets from Profile")
    public void openWidgets() {
        WebElement row = scrollIntoView(Locators.visibleButton("Widgets"), "Widgets row");
        Waits.resilient(row::click);
        new SettingsPage(driver, factory).waitForWidgetsSheet();
    }

    @Step("Open Settings")
    public SettingsPage openSettings() {
        tap(Locators.profileSettingsButton(), "Profile gear");
        return new SettingsPage(driver, factory).waitUntilLoaded();
    }

    @Step("Open Reset Goals")
    public ResetGoalsPage openResetGoals() {
        tap(Locators.visibleButton("Reset Goals"), "Profile pencil");
        return new ResetGoalsPage(driver, factory).waitUntilLoaded();
    }

    /**
     * The Daily Goals grid as caption → value: "Calories" → "2234 kcal",
     * "Protein" → "167g", "Carbs", "Fat". Paired by order, not position: iOS 26
     * lists each caption before its value, iOS 27 a row's captions before its
     * values (seen on the 12 Pro), but both keep captions and values in grid order.
     */
    public java.util.Map<String, String> dailyGoals() {
        List<String> texts = allTexts();
        int start = texts.indexOf("Daily Goals");
        List<String> captions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = start + 1; start >= 0 && i < texts.size() && values.size() < DAILY_GOALS.size(); i++) {
            String text = texts.get(i);
            if (DAILY_GOALS.contains(text) && captions.size() < DAILY_GOALS.size()) {
                captions.add(text);
            } else if (text.matches("[0-9][0-9,]* ?(kcal|g)")) {
                values.add(text);
            }
        }
        if (captions.size() != DAILY_GOALS.size() || values.size() != DAILY_GOALS.size()) {
            throw new AssertionError("No Daily Goals grid on Profile; texts: " + texts);
        }
        java.util.Map<String, String> goals = new java.util.LinkedHashMap<>();
        for (int i = 0; i < captions.size(); i++) {
            goals.put(captions.get(i), values.get(i));
        }
        return goals;
    }

    private static final List<String> DAILY_GOALS = List.of("Calories", "Protein", "Carbs", "Fat");

    private List<String> allTexts() {
        List<String> texts = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        return texts;
    }
}
