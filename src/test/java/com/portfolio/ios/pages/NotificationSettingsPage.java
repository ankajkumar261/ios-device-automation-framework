package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

/**
 * Profile → Notifications ({@code NotificationSettingsView.swift}): four
 * switches — "Daily streak reminder", "Meal logging reminders", "Water
 * reminders", "Catch-up reminder" — and "Done". When any is on but iOS has
 * notifications off, a banner "Notifications are off in iOS Settings" shows.
 */
public final class NotificationSettingsPage extends BasePage {

    public static final String PERMISSION_BANNER = "Notifications are off in iOS Settings";

    public NotificationSettingsPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    @Step("Wait for Notifications")
    public NotificationSettingsPage waitUntilLoaded() {
        visible(Locators.switchLabelled("Daily streak reminder"), Waits.MEDIUM, "Notifications switches");
        return this;
    }

    public boolean isOn(String label) {
        return "1".equals(Waits.resilientGet(() -> driver.findElement(Locators.switchLabelled(label))
                .getAttribute("value")));
    }

    /**
     * Flips a switch and waits for it to change. Turning one on may make the
     * app ask iOS for permission; a prompt, if any, is declined.
     */
    @Step("Toggle {label}")
    public NotificationSettingsPage toggle(String label) {
        boolean before = isOn(label);
        tap(Locators.switchControl(label), "notification switch");
        new SystemDialogPage(driver, factory).declineSystemAlertIfPresent(Waits.QUICK);
        Waits.untilTrue(Waits.SHORT, label + " to turn " + (before ? "off" : "on"), () -> isOn(label) != before);
        return this;
    }

    public boolean showsPermissionBanner() {
        return Waits.isPresent(driver, Locators.staticText(PERMISSION_BANNER));
    }

    public static final String PERMISSION_HINT = "Enable them so your reminders can fire.";

    public boolean showsPermissionHint() {
        return Waits.isPresent(driver, Locators.staticText(PERMISSION_HINT));
    }

    /**
     * The banner's button: "Enable" while iOS has never been asked, "Open
     * Settings" once notifications were turned down (iOS asks only once).
     * Null without a banner.
     */
    public String bannerAction() {
        for (String label : java.util.List.of("Enable", "Open Settings")) {
            if (Waits.isPresent(driver, Locators.visibleButton(label))) {
                return label;
            }
        }
        return null;
    }

    @Step("Tap the banner's button")
    public NotificationSettingsPage tapBannerAction() {
        String action = bannerAction();
        if (action == null) {
            throw new AssertionError("No notifications banner to tap");
        }
        tap(Locators.visibleButton(action), "notifications banner");
        return this;
    }

    /** Waits until the banner offers {@code action} (it refreshes after the iOS prompt is answered). */
    public NotificationSettingsPage waitForBannerAction(String action) {
        Waits.untilTrue(Waits.SHORT, "banner button '" + action + "'", () -> action.equals(bannerAction()));
        return this;
    }

    @Step("Close Notifications")
    public ProfilePage done() {
        tap(Locators.visibleButton("Done"), "Notifications");
        waitForGone(Locators.switchLabelled("Daily streak reminder"), Waits.SHORT);
        return new ProfilePage(driver, factory).waitUntilLoaded();
    }
}
