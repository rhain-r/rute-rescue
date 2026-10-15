package com.rutarescue.repository.firestore;

import java.util.Locale;

/**
 * Turns a raw Firestore field value into the Java type the app needs, without ever throwing.
 * Documents created by hand in the Firebase console sometimes hold the wrong type (a number
 * typed as text, for example); the app then reads the value it can and uses a safe default
 * instead of crashing.
 */
public final class FieldValues {

    private FieldValues() {
    }

    public static String asText(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Numbers as they are; digits saved as text are parsed; anything else is 0. */
    public static long asLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            String text = ((String) value).strip();
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException notWhole) {
                try {
                    return (long) Double.parseDouble(text);
                } catch (NumberFormatException notNumber) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    public static double asDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).strip());
            } catch (NumberFormatException notNumber) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /** true only for a boolean true or the text "true" (any capitals). */
    public static boolean asBool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return value instanceof String && "true".equals(((String) value).strip().toLowerCase(Locale.ROOT));
    }
}
