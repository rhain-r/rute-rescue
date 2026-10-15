package com.rutarescue.validation;

import com.rutarescue.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Sign-in and registration checks (regular expressions + String methods). */
public final class AccountValidator {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    public static final int MIN_PASSWORD_LENGTH = 8;

    private AccountValidator() {
    }

    public static String requireEmail(String raw) throws ValidationException {
        String email = raw == null ? "" : raw.strip();
        if (email.isEmpty()) throw new ValidationException("email", "Enter your e-mail address.");
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("email", "Enter a valid e-mail address, e.g. name@example.com.");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    public static String requirePassword(String raw) throws ValidationException {
        if (raw == null || raw.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password", "Use at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!HAS_LETTER.matcher(raw).matches() || !HAS_DIGIT.matcher(raw).matches()) {
            throw new ValidationException("password", "Use at least one letter and one number.");
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                throw new ValidationException("password", "Passwords cannot contain spaces.");
            }
        }
        return raw;
    }
}
