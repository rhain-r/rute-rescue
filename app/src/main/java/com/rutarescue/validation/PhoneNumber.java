package com.rutarescue.validation;

import com.rutarescue.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Philippine mobile numbers (Lab 7 / DOCX section 9): remove spaces and hyphens, then
 * require exactly 11 ASCII digits starting with 09, e.g. "0917-123-4567" -> "09171234567".
 *
 * A valid format does not prove that the number is active or belongs to the family.
 */
public final class PhoneNumber {

    /** matcher(...).matches() checks the whole value, so no ^ or $ anchors are needed. */
    private static final Pattern MOBILE = Pattern.compile("09[0-9]{9}");

    public static final String FORMAT_HINT =
            "must normalize to 11 digits beginning with 09; use 0917-123-4567 or 09171234567.";

    private PhoneNumber() {
    }

    /** Removes only the accepted separators (regular spaces and hyphens). */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.strip().replace(" ", "").replace("-", "");
    }

    public static boolean isValidMobile(String normalized) {
        return normalized != null && MOBILE.matcher(normalized).matches();
    }

    /** Normalizes and validates; throws a ValidationException with guidance when invalid. */
    public static String requireMobile(String field, String raw) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(field, TextRules.label(field) + " is required.");
        }
        String normalized = normalize(raw);
        if (!isValidMobile(normalized)) {
            throw new ValidationException(field, TextRules.label(field) + " " + FORMAT_HINT);
        }
        return normalized;
    }

    /** Pretty display format "0917 123 4567" for a normalized number. */
    public static String format(String normalized) {
        if (!isValidMobile(normalized)) return normalized == null ? "" : normalized;
        return normalized.substring(0, 4) + " " + normalized.substring(4, 7) + " " + normalized.substring(7);
    }
}
