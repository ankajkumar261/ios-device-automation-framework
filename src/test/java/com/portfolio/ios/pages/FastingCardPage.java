package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import com.portfolio.ios.core.Waits;
import io.appium.java_client.ios.IOSDriver;
import io.qameta.allure.Step;

import java.util.List;

/**
 * Home's fasting banner: a fixed button above the tab bar, shown while a fast
 * runs and for a day after one ends until dismissed ({@code Features/Fasting/FastingCard.swift},
 * app 1.0.15).
 *
 * <p>Running: {@code 'Intermittent Fasting, Fasting in progress · 16:8 protocol, 00:00:11'}
 * with children {@code 'Intermittent Fasting'}, {@code 'Fasting in progress · 16:8 protocol'}
 * and the timer {@code 'HH:MM:SS'}. Ended: {@code 'Intermittent Fasting, Share Your Fast, Ended'};
 * completed: {@code '… Completed'} — both with a separate {@code 'Dismiss'} button.
 * Tapping the banner opens the detail sheet: {@code 'Elapsed' + timer},
 * {@code 'Fasting Started' + stamp}, {@code 'Fasting End' + stamp} and {@code 'End Fast'}
 * (running) or {@code 'Fast ended · 16:8 protocol' + duration} / {@code 'Fast complete! · …'}
 * (finished) with {@code 'Close'}.
 *
 * <p>Public API is unchanged ({@link #title}, {@link #protocolLabel}, {@link #stamps},
 * {@link #elapsedSeconds}, {@link #length}, {@link #tapEndFast}, {@link #dismiss}),
 * so existing tests keep their intent: titles map to {@code "Fasting in progress"} /
 * {@code "Fast ended"} / {@code "Fast complete!"}; protocol, stamps and length are read
 * from the banner (running) or its sheet (finished).
 */
public final class FastingCardPage extends BasePage {

    public FastingCardPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /** True when the banner button is in the tree (on Home, sheet open or not). */
    public boolean isShowing() {
        return !driver.findElements(Locators.fastingBanner()).isEmpty();
    }

    /** "Fasting in progress", "Fast complete!" or "Fast ended" (mapped from the banner). */
    public String title() {
        String label = bannerLabel();
        if (label.contains("Fasting in progress")) {
            return "Fasting in progress";
        }
        if (label.contains("Completed")) {
            return "Fast complete!";
        }
        if (label.contains("Ended")) {
            return "Fast ended";
        }
        throw new AssertionError("Unknown fasting banner: " + label);
    }

    /** "16:8 protocol" — from the banner while running, else from the detail sheet's title. */
    public String protocolLabel() {
        String label = bannerLabel();
        if (label.contains("protocol")) {
            return protocolFrom(label);
        }
        openSheet();
        try {
            for (String text : visibleTextsInOrder()) {
                if ((text.startsWith("Fast ended") || text.startsWith("Fast complete!")) && text.contains("· ")) {
                    return protocolFrom(text);
                }
            }
            throw new AssertionError("No protocol in the fasting sheet; texts: " + visibleTextsInOrder());
        } finally {
            closeSheet();
        }
    }

    /** The "Fasting Started" and "Fasting End" stamps from the detail sheet, in that order. */
    public List<String> stamps() {
        openSheet();
        try {
            List<String> texts = visibleTextsInOrder();
            String started = textAfter(texts, "Fasting Started");
            String end = textAfter(texts, "Fasting End");
            return List.of(FastingText.normalise(started), FastingText.normalise(end));
        } finally {
            closeSheet();
        }
    }

    /**
     * Running timer in seconds: banner {@code "00:00:05"} → 5, {@code "05:00:09"} → 18009.
     * The banner uses zero-padded {@code HH:MM:SS} and ticks every second.
     */
    public int elapsedSeconds() {
        String label = bannerLabel();
        String[] parts = label.split(", ");
        String timer = parts[parts.length - 1].trim();
        if (!timer.matches("([0-9]+:)?[0-9]{1,2}:[0-9]{2}")) {
            // Fall back to any timer-shaped text in the tree (same pattern the old top card used).
            for (String text : visibleTextsInOrder()) {
                if (text.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) {
                    timer = text;
                    break;
                }
            }
        }
        if (!timer.matches("([0-9]+:)?[0-9]{1,2}:[0-9]{2}")) {
            throw new AssertionError("No elapsed clock on the fasting banner: " + label);
        }
        int seconds = 0;
        for (String part : timer.split(":")) {
            seconds = seconds * 60 + Integer.parseInt(part);
        }
        return seconds;
    }

    /**
     * Finished banner's length from the detail sheet: {@code "1m"} as-is;
     * the completed sheet's {@code "16 hours"} is normalised to {@code "16h 0m"}
     * so duration assertions keep their form.
     */
    public String length() {
        openSheet();
        try {
            List<String> texts = visibleTextsInOrder();
            for (String text : texts) {
                String norm = FastingText.normalise(text);
                if (norm.matches("([0-9]+h )?[0-9]+m")) {
                    return norm;
                }
                if (norm.matches("[0-9]+ hours?")) {
                    return norm.replaceAll("[^0-9]", "") + "h 0m";
                }
                if (norm.matches("[0-9]+ minutes?")) {
                    return norm.replaceAll("[^0-9]", "") + "m";
                }
            }
            throw new AssertionError("No fast length in the fasting sheet: " + texts);
        } finally {
            closeSheet();
        }
    }

    @Step("End Fast on Home")
    public FastingEndAlert tapEndFast() {
        bringIntoView();
        openSheet();
        tap(Locators.visibleButton("End Fast"), "fasting sheet");
        return new FastingEndAlert(driver, factory).waitUntilShown();
    }

    @Step("Dismiss the fasting card")
    public HomePage dismiss() {
        bringIntoView();
        tap(Locators.visibleButton("Dismiss"), "fasting card");
        Waits.untilTrue(Waits.SHORT, "fasting card to go", () -> !isShowing());
        return new HomePage(driver, factory).waitUntilLoaded();
    }

    /**
     * The banner is fixed above the tab bar (outside the scroll view), so it needs
     * no pager swipe — just be at the top and wait for it.
     */
    private void bringIntoView() {
        new HomePage(driver, factory).scrollToTop();
        if (!driver.findElements(Locators.fastingBanner()).isEmpty()) {
            return;
        }
        visible(Locators.fastingBanner(), Waits.SHORT, "fasting banner");
    }

    /** The banner button's full label, present whether its sheet is open or not. */
    private String bannerLabel() {
        List<org.openqa.selenium.WebElement> banners = driver.findElements(Locators.fastingBanner());
        if (banners.isEmpty()) {
            throw new AssertionError("No fasting card on Home");
        }
        return Waits.resilientGet(() -> banners.get(0).getAttribute("label"));
    }

    private static String protocolFrom(String label) {
        int dot = label.indexOf("· ");
        if (dot < 0) {
            throw new AssertionError("No protocol in '" + label + "'");
        }
        return label.substring(dot + 2).split(",")[0].trim();
    }

    private static String textAfter(List<String> texts, String caption) {
        int at = texts.indexOf(caption);
        if (at < 0 || at + 1 >= texts.size()) {
            throw new AssertionError("No text after '" + caption + "'; texts: " + texts);
        }
        return texts.get(at + 1);
    }

    private boolean isSheetOpen() {
        return Waits.isPresent(driver, Locators.staticText("Fasting Started"));
    }

    /** Taps the banner until the detail sheet shows (its "Fasting Started" row). */
    private void openSheet() {
        if (isSheetOpen()) {
            return;
        }
        tap(Locators.fastingBanner(), "fasting banner");
        visible(Locators.staticText("Fasting Started"), Waits.SHORT, "fasting detail sheet");
    }

    /** Closes the detail sheet if open. */
    private void closeSheet() {
        if (!isSheetOpen()) {
            return;
        }
        tap(Locators.visibleButton("Close"), "fasting sheet");
        waitForGone(Locators.staticText("Fasting Started"), Waits.SHORT);
    }
}
