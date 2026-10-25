package com.portfolio.ios.pages;

import com.portfolio.ios.core.Config;
import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings, opened from Profile's gear ({@code SettingsView.swift}), and the
 * sheets its rows open.
 *
 * <p>Verified on the simulator: "Settings", "App Theme" with Buttons "Dark" /
 * "Light" / "System" (the choice is shown by colour only — see
 * {@link BasePage#screenBrightness()}), rows Language, Widgets, Privacy
 * Policy, Terms of Service, Sources & References, Support, Share App, Rate
 * Us, Developer Apps, About Us, then "App Version" and its number; "Close".
 */
public final class SettingsPage extends BasePage {

    public static final List<String> ROWS = List.of("Language", "Widgets", "Privacy Policy", "Terms of Service",
            "Sources & References", "Support", "Share App", "Rate Us", "Developer Apps", "About Us");

    public SettingsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for Settings")
    public SettingsPage waitUntilLoaded() {
        // In the tree rather than visible: Settings may be scrolled past "App Theme".
        Waits.until(Waits.MEDIUM, "Settings 'App Theme'",
                () -> driver.findElements(Locators.staticText("App Theme")).isEmpty() ? null : Boolean.TRUE);
        return this;
    }

    /** Settings rows present, in screen order. */
    public List<String> rows() {
        List<String> rows = new ArrayList<>();
        boolean inSettings = false;
        for (String[] element : elementsInOrder()) {
            // Profile stays in the tree underneath (its own "Widgets" row too):
            // only read what follows Settings' "App Theme".
            inSettings |= "XCUIElementTypeStaticText".equals(element[0]) && "App Theme".equals(element[1]);
            if (inSettings && "XCUIElementTypeButton".equals(element[0]) && ROWS.contains(element[1])
                    && !rows.contains(element[1])) {
                rows.add(element[1]);
            }
        }
        return rows;
    }

    public boolean hasThemeButton(String theme) {
        return Waits.isPresent(driver, Locators.visibleButton(theme));
    }

    /** The number after "App Version". */
    public String appVersion() {
        List<String> texts = new ArrayList<>();
        for (String[] element : elementsInOrder()) {
            if ("XCUIElementTypeStaticText".equals(element[0])) {
                texts.add(element[1]);
            }
        }
        int at = texts.indexOf("App Version");
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No App Version on Settings: " + texts);
        }
        return texts.get(at + 1);
    }

    /** "Dark", "Light" or "System"; waits for the screen to follow when it is Dark or Light. */
    @Step("Theme: {theme}")
    public SettingsPage chooseTheme(String theme) {
        tap(Locators.visibleButton(theme), "App Theme");
        if (!"System".equals(theme)) {
            boolean dark = "Dark".equals(theme);
            Waits.untilTrue(Waits.SHORT, "the screen to turn " + theme, () -> looksDark() == dark);
        }
        return this;
    }

    @Step("Close Settings")
    public ProfilePage close() {
        tap(Locators.visibleButton("Close"), "Settings");
        waitForGone(Locators.staticText("App Theme"), Waits.SHORT);
        return new ProfilePage(driver, factory).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- Language

    @Step("Open Language")
    public SettingsPage openLanguage() {
        tap(Locators.visibleButton("Language"), "Settings");
        visible(Locators.visibleButton("System default, English"), Waits.SHORT, "Language list");
        return this;
    }

    /** Label of the language marked selected (value "1"). */
    public String selectedLanguage() {
        for (WebElement option : driver.findElements(Locators.byType("XCUIElementTypeButton"))) {
            if ("1".equals(Waits.resilientGet(() -> option.getAttribute("value")))) {
                return Waits.resilientGet(() -> option.getAttribute("label"));
            }
        }
        return null;
    }

    public boolean listsLanguage(String label) {
        return !driver.findElements(Locators.button(label)).isEmpty();
    }

    // ---------------------------------------------------------------- simple sheets

    @Step("Open Sources & References")
    public SettingsPage openSources() {
        tap(Locators.visibleButton("Sources & References"), "Settings");
        visible(Locators.staticText("AI-Generated Nutrition Information"), Waits.SHORT, "Sources & References");
        return this;
    }

    public boolean showsText(String label) {
        return Waits.isPresent(driver, Locators.staticText(label));
    }

    /** Closes a sheet that has "Done" (Language, Sources & References, Widgets). */
    @Step("Close the sheet")
    public SettingsPage doneSheet() {
        tap(Locators.visibleButton("Done"), "Settings sheet");
        return waitUntilLoaded();
    }

    @Step("Open Widgets from Settings")
    public SettingsPage openWidgets() {
        tap(Locators.visibleButton("Widgets"), "Settings");
        waitForWidgetsSheet();
        return this;
    }

    public void waitForWidgetsSheet() {
        visible(Locators.staticText("Streak"), Waits.SHORT, "Widgets sheet 'Streak'");
    }

    /** Closes the Widgets sheet, wherever it was opened from. */
    @Step("Close Widgets")
    public void closeWidgets() {
        tap(Locators.visibleButton("Done"), "Widgets sheet");
        waitForGone(Locators.staticText("Streak"), Waits.SHORT);
    }

    // ---------------------------------------------------------------- web pages

    /**
     * Opens Privacy Policy, Terms of Service or About Us — a web page from the
     * production site inside a sheet — and returns the page's first text once
     * it has loaded.
     */
    @Step("Open {title}")
    public String openWebPage(String title) {
        WebElement row = scrollIntoView(Locators.visibleButton(title), title + " row");
        Waits.resilient(row::click);
        visible(Locators.staticText(title), Waits.SHORT, title + " sheet");
        return Waits.until(Waits.MEDIUM, title + " page to load (network)", () -> {
            for (WebElement text : driver.findElements(Locators.webViewTexts())) {
                String label = Waits.resilientGet(() -> text.getAttribute("label"));
                if (label != null && !label.isBlank()) {
                    return label;
                }
            }
            return null;
        });
    }

    @Step("Close the web page")
    public SettingsPage closeWebPage() {
        tap(Locators.visibleButton("Close"), "web page sheet");
        waitForGone(Locators.byType("XCUIElementTypeWebView"), Waits.SHORT);
        return waitUntilLoaded();
    }

    // ---------------------------------------------------------------- Support

    /**
     * Taps Support, which opens a {@code mailto:} link
     * ({@code SettingsView.swift}). Where nothing can open it (the simulator
     * has no Mail app) the app shows "Mail Not Available" — returned as its
     * message and closed with OK. On an iPhone the Mail app takes over with
     * the draft — returned as "composer" — and the app is brought straight
     * back, so nothing is ever sent. Mail is only recognised by which app is
     * in front, never searched: its inbox makes every lookup there very slow
     * (141 s for this step on the 11 Pro Max). A system alert Mail raises about
     * its account ("Enter the Google password … in Settings") is closed with
     * Cancel.
     */
    @Step("Tap Support")
    public String tapSupport() {
        tap(Locators.visibleButton("Support"), "Settings");
        String outcome = Waits.until(Waits.MEDIUM, "mail alert or the Mail app", () -> {
            if (!Config.bundleId().equals(activeBundleId())) {
                return "composer";
            }
            if (Waits.isPresent(driver, Locators.alertTitled("Mail Not Available"))) {
                List<WebElement> texts = driver.findElements(Locators.alertTexts());
                return texts.size() > 1 ? Waits.resilientGet(() -> texts.get(1).getAttribute("label")) : "";
            }
            return null;
        });
        if ("composer".equals(outcome)) {
            // Mail's account alert can come up a moment after Mail does, and stays over any app.
            Waits.untilTrue(Waits.MEDIUM, "the app back in front", () -> {
                dismissMailAccountAlert();
                if (!Config.bundleId().equals(activeBundleId())) {
                    driver.activateApp(Config.bundleId());
                }
                return Config.bundleId().equals(activeBundleId());
            });
        } else {
            tap(Locators.alertActionButton("OK"), "Mail Not Available");
            waitForGone(Locators.alertTitled("Mail Not Available"), Waits.SHORT);
        }
        waitUntilLoaded();
        return outcome;
    }

    private String activeBundleId() {
        Object info = driver.executeScript("mobile: activeAppInfo");
        return info instanceof java.util.Map<?, ?> map && map.get("bundleId") != null
                ? String.valueOf(map.get("bundleId")) : null;
    }

    /** Closes Mail's system alert about its account (buttons Settings and Cancel), if one is up. */
    private void dismissMailAccountAlert() {
        try {
            Object buttons = driver.executeScript("mobile: alert", java.util.Map.of("action", "getButtons"));
            if (buttons instanceof List<?> labels && labels.contains("Settings") && labels.contains("Cancel")) {
                driver.executeScript("mobile: alert", java.util.Map.of("action", "dismiss", "buttonLabel", "Cancel"));
            }
        } catch (RuntimeException noAlert) {
            // Nothing to close.
        }
    }
}
