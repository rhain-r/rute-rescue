package com.rutarescue.validation;

import java.util.regex.Pattern;

/**
 * Safe integer parsing from Lab 6/7.
 *
 * Two different exceptions are used on purpose (Lab 6 Part B):
 *  - NumberFormatException: the text is not a whole number ("four", "4.0", "+4", overflow)
 *  - IllegalArgumentException: the number exists but breaks a range rule (0, 101)
 * Callers catch them separately and show different messages.
 */
public final class InputValidator {

    /** Count syntax only: ASCII digits, no signs, decimals or exponent notation. */
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    public static final int MIN_FAMILY_SIZE = 1;
    public static final int MAX_FAMILY_SIZE = 100;
    public static final int MAX_AGE = 120;

    private InputValidator() {
    }

    /** Lab 6: parses a whole number that must be greater than zero. */
    public static int parsePositiveInteger(String raw) {
        int value = parseDigits(raw, "Value");
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be greater than zero.");
        }
        return value;
    }

    /** Lab 7: family size uses digits 0-9 only and must be between 1 and 100. */
    public static int parseFamilySize(String raw) {
        int size = parseDigits(raw, "Family size");
        if (size < MIN_FAMILY_SIZE || size > MAX_FAMILY_SIZE) {
            throw new IllegalArgumentException("Family size must be between 1 and 100.");
        }
        return size;
    }

    /** Ages are whole numbers from 0 (infant) to 120. */
    public static int parseAge(String raw) {
        int age = parseDigits(raw, "Age");
        if (age > MAX_AGE) {
            throw new IllegalArgumentException("Age must be between 0 and 120.");
        }
        return age;
    }

    private static int parseDigits(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new NumberFormatException(label + " is required.");
        }
        String value = raw.strip();
        if (!DIGITS.matcher(value).matches()) {
            throw new NumberFormatException(label + " must use digits 0-9 only, e.g. 4; no signs or decimals.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException tooLarge) {
            // The regex accepted the digits, so the only remaining failure is overflow.
            throw new NumberFormatException(label + " is too large.");
        }
    }
}
