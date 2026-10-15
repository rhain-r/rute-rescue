package com.rutarescue.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small text formatters shared by the screens (time stamps, distances, durations). */
public final class Formats {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);

    private Formats() {
    }

    /** "just now", "12 min ago", "3 h ago", "Oct 6, 9:42 AM". */
    public static String relative(long then, long now) {
        if (then <= 0) return "time unknown";
        long minutes = (now - then) / 60_000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h ago";
        return dateTime(then);
    }

    public static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    public static String dateTime(long epochMillis) {
        if (epochMillis <= 0) return "unknown time";
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /** "850 m" or "2.3 km". */
    public static String distance(double meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format(Locale.US, "%.1f km", meters / 1000.0);
    }

    /** "18 min" or "1 h 05 min". */
    public static String duration(long seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        if (minutes < 60) return minutes + " min";
        return String.format(Locale.US, "%d h %02d min", minutes / 60, minutes % 60);
    }

    /** "Good morning", "Good afternoon" or "Good evening" for the hero greeting. */
    public static String greeting(int hourOfDay) {
        if (hourOfDay < 12) return "Good morning";
        if (hourOfDay < 18) return "Good afternoon";
        return "Good evening";
    }
}
