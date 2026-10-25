package com.portfolio.ios.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a picker wheel's displayed value into its position on the wheel.
 *
 * <p>Needed because {@link Gestures#setPickerWheelValue} cannot compare row text
 * directly — this app's SwiftUI pickers expose no readable labels (see that
 * method for the tree evidence) — so ordering has to come from the value the
 * wheel itself reports. Different wheels need different orderings: the
 * birth-year wheel holds plain integers and sorts numerically, while the
 * eating-window wheel holds times like "8:00 pm" that must be read as
 * 20:00, not sorted as text.
 *
 * <p>Kept separate from {@link Gestures} so the generic gesture helper holds no
 * knowledge of this app's date and time formats.
 */
public final class WheelValues {

    /** Matches the app's 12-hour clock format, e.g. "8:00 am" or "12:00 pm". */
    private static final Pattern CLOCK_12H = Pattern.compile("^(\\d{1,2}):00\\s*([ap]m)$",
            Pattern.CASE_INSENSITIVE);

    private WheelValues() {
    }

    /** Sort key for a wheel of plain integers, e.g. a year. */
    public static int numeric(String value) {
        return Integer.parseInt(value.trim());
    }

    /**
     * Sort key for the app's 12-hour clock, normalised to 24-hour.
     *
     * <p>Text order would be actively wrong here: "12:00 am" sorts before
     * "8:00 am" as a string but after it on the wheel, so a string-based
     * comparison walks the wheel the wrong way and never converges.
     */
    public static int hour24(String value) {
        Matcher matcher = CLOCK_12H.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a 12-hour clock value of the form '8:00 pm'");
        }
        int hour = Integer.parseInt(matcher.group(1)) % 12;
        if ("pm".equalsIgnoreCase(matcher.group(2))) {
            hour += 12;
        }
        return hour;
    }
}