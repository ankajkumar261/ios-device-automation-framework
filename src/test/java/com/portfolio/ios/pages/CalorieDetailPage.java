package com.portfolio.ios.pages;

import com.portfolio.ios.core.DriverFactory;
import io.appium.java_client.ios.IOSDriver;

import java.util.List;

/**
 * Reads Calorie Detail, opened from Home's goal button
 * ({@code CalorieDetailView.swift}); opening, switching range and closing
 * live on {@link HomePage}.
 *
 * <p>Verified on the simulator, visible texts in order — Day: "Calories",
 * "TODAY", "305", "kcal", "Total consumed today", …, "of 2,234 goal", …, then
 * the macros "3g", "79g", "1g" and three "taken". Week / Month / Year:
 * "Calories · Week", the range "SEP 30 – OCT 6", the total, "kcal", "AVG",
 * the average, "/day", "Total over the past week".
 */
public final class CalorieDetailPage extends BasePage {

    public CalorieDetailPage(IOSDriver driver, DriverFactory factory) {
        super(driver, factory);
    }

    /** "Calories" (Day) or "Calories · Week" / "· Month" / "· Year". */
    public String title() {
        return texts().get(0);
    }

    /** "TODAY", or the range such as "SEP 30 – OCT 6". */
    public String range() {
        return texts().get(1);
    }

    /** The big total under the range. */
    public int total() {
        return digits(texts().get(2));
    }

    /** The per-day average next to "AVG" (Week / Month / Year). */
    public int average() {
        List<String> texts = texts();
        int at = texts.indexOf("AVG");
        if (at < 0) {
            throw new AssertionError("No AVG on Calorie Detail: " + texts);
        }
        return digits(texts.get(at + 1));
    }

    public boolean showsText(String text) {
        return texts().contains(text);
    }

    /** Day view: "of 2,234 goal". */
    public String goalLine() {
        for (String text : texts()) {
            if (text.startsWith("of ") && text.endsWith(" goal")) {
                return text;
            }
        }
        throw new AssertionError("No goal line on Calorie Detail: " + texts());
    }

    /** Day view: protein, carbs and fat taken — the three texts before the first "taken". */
    public List<String> macrosTaken() {
        List<String> texts = texts();
        int at = texts.indexOf("taken");
        if (at < 3) {
            throw new AssertionError("No macros on Calorie Detail: " + texts);
        }
        return texts.subList(at - 3, at);
    }

    private List<String> texts() {
        return visibleTextsInOrder();
    }
}
