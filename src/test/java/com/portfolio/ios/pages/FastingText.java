package com.portfolio.ios.pages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The fasting screens' date and time formats ({@code FastingFormat.swift}),
 * en_US: times "1:32 PM", card stamps "Mon, Oct 5, 1:32 PM", history dates
 * "Oct 5, 2026", durations "2m" / "16h 0m".
 *
 * <p>iOS may put a narrow no-break space before AM/PM; every parse
 * normalises it to a plain space first.
 */
public final class FastingText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.US);
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private FastingText() {
    }

    public static String normalise(String text) {
        return text.replace(' ', ' ').replace(' ', ' ').trim();
    }

    public static LocalTime parseTime(String text) {
        return LocalTime.parse(normalise(text), TIME);
    }

    public static String time(LocalTime time) {
        return TIME.format(time);
    }

    public static String dayAndTime(LocalDateTime when) {
        return DAY_AND_TIME.format(when);
    }

    public static String historyDate(LocalDate day) {
        return HISTORY_DATE.format(day);
    }

    /** A card stamp, "Mon, Oct 5, 1:32 PM". */
    public static boolean isDayAndTime(String text) {
        return normalise(text).matches("[A-Z][a-z]{2}, [A-Z][a-z]{2} [0-9]{1,2}, [0-9]{1,2}:[0-9]{2} [AP]M");
    }

    /** "2m" → 2, "16h 0m" → 960. */
    public static int minutes(String duration) {
        String text = normalise(duration);
        int hours = text.contains("h") ? Integer.parseInt(text.substring(0, text.indexOf('h')).trim()) : 0;
        String rest = text.contains("h") ? text.substring(text.indexOf('h') + 1) : text;
        int minutes = Integer.parseInt(rest.replace("m", "").trim());
        return hours * 60 + minutes;
    }
}
