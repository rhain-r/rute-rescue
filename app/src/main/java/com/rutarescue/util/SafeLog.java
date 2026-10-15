package com.rutarescue.util;

import android.util.Log;

import java.util.regex.Pattern;

/**
 * Logging that never writes personal data (DOCX section 12: logs exclude names, phone
 * numbers, addresses, proof URLs and precise saved locations).
 *
 * Rule for the team: only log event names and counts, never user input. As a safety net,
 * phone numbers, e-mail addresses, URLs and precise coordinates are masked.
 */
public final class SafeLog {

    private static final Pattern PHONE = Pattern.compile("(\\+?63|0)9[0-9 -]{9,12}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern PRECISE_COORDINATE = Pattern.compile("(-?[0-9]{1,3}\\.[0-9]{2})[0-9]{2,}");

    private SafeLog() {
    }

    public static void i(String tag, String message) {
        Log.i(tag, redact(message));
    }

    public static void w(String tag, String message) {
        Log.w(tag, redact(message));
    }

    /** Logs only the exception type, not its message (messages may echo user input). */
    public static void w(String tag, String message, Throwable error) {
        Log.w(tag, redact(message) + " [" + (error == null ? "no error" : error.getClass().getSimpleName()) + "]");
    }

    static String redact(String message) {
        if (message == null) return "";
        String result = URL.matcher(message).replaceAll("[url]");
        result = EMAIL.matcher(result).replaceAll("[email]");
        result = PHONE.matcher(result).replaceAll("[phone]");
        return PRECISE_COORDINATE.matcher(result).replaceAll("$1~");
    }
}
