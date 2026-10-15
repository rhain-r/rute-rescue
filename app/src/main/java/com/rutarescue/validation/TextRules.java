package com.rutarescue.validation;

import com.rutarescue.exception.ValidationException;

/**
 * Shared text checks ported from Lab 7's requiredText().
 *
 * String methods are clearer than regular expressions for these rules:
 * strip() removes outer spaces, isBlank() detects empty input, contains("|") protects the
 * pipe-delimited file format, and Character.isISOControl() finds hidden control characters.
 */
public final class TextRules {

    /** Unicode line and paragraph separators also break a one-line record. */
    private static final char LINE_SEPARATOR = ' ';
    private static final char PARAGRAPH_SEPARATOR = ' ';

    private TextRules() {
        // utility class
    }

    /**
     * Required single-line text: strips outer whitespace, keeps internal spacing, and
     * rejects blanks, the pipe delimiter and control characters.
     */
    public static String requiredText(String field, String raw) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(field, label(field) + " is required.");
        }
        String value = raw.strip();
        checkSingleLineSafe(field, value);
        return value;
    }

    /** Optional single-line text: blank becomes "", otherwise the same safety checks. */
    public static String optionalText(String field, String raw) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.strip();
        checkSingleLineSafe(field, value);
        return value;
    }

    /**
     * Multi-line text (advisory messages, justifications): newlines are allowed, other
     * control characters are not, and the length must be within limits.
     */
    public static String multilineText(String field, String raw, int minLength, int maxLength)
            throws ValidationException {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(field, label(field) + " is required.");
        }
        String value = raw.strip();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\n' && (Character.isISOControl(c) || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR)) {
                throw new ValidationException(field, label(field) + " contains an invalid control character.");
            }
        }
        checkLength(field, value, minLength, maxLength);
        return value;
    }

    public static void checkLength(String field, String value, int minLength, int maxLength)
            throws ValidationException {
        int length = value.codePointCount(0, value.length());
        if (length < minLength) {
            throw new ValidationException(field, label(field) + " must have at least " + minLength + " characters.");
        }
        if (length > maxLength) {
            throw new ValidationException(field, label(field) + " must have at most " + maxLength + " characters.");
        }
    }

    private static void checkSingleLineSafe(String field, String value) throws ValidationException {
        if (value.contains("|")) {
            throw new ValidationException(field, label(field) + " must not contain the | character.");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                throw new ValidationException(field, label(field) + " must be a single line without control characters.");
            }
        }
    }

    /** Turns a field key such as "headName" into a readable label ("Head name"). */
    static String label(String field) {
        if (field == null || field.isEmpty()) return "Value";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i == 0) {
                out.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                out.append(' ').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
