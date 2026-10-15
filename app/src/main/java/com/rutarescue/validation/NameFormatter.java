package com.rutarescue.validation;

import com.rutarescue.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Person-name rule from Lab 7: letters (including accented and non-Latin letters),
 * spaces, apostrophes, periods and hyphens only, with at least one letter. The value is
 * converted to Title Case and a trailing plain number becomes a Roman-numeral suffix,
 * e.g. "juan dela cruz 3" -> "Juan Dela Cruz III".
 */
public final class NameFormatter {

    /** A name followed by a space and a 1-2 digit generation number. */
    private static final Pattern GENERATION_SUFFIX = Pattern.compile("(.*\\S)\\s+([0-9]{1,2})");

    private NameFormatter() {
    }

    public static String requirePersonName(String field, String raw) throws ValidationException {
        String value = TextRules.requiredText(field, raw);

        String suffix = "";
        Matcher matcher = GENERATION_SUFFIX.matcher(value);
        if (matcher.matches()) {
            int generation = Integer.parseInt(matcher.group(2));
            if (generation < 1 || generation > 39) {
                throw new ValidationException(field, TextRules.label(field) + " may only end with a generation number from 1 to 39.");
            }
            value = matcher.group(1);
            suffix = " " + toRoman(generation);
        }

        int letters = 0;
        boolean hasInvalidCharacter = false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                letters++;
            } else if (!isAllowedPunctuation(codePoint)) {
                hasInvalidCharacter = true;
            }
            i += Character.charCount(codePoint);
        }
        if (letters == 0) {
            throw new ValidationException(field, TextRules.label(field) + " must contain at least one letter.");
        }
        if (hasInvalidCharacter) {
            throw new ValidationException(field, TextRules.label(field)
                    + " may contain only letters, spaces, apostrophes, periods and hyphens.");
        }
        return toTitleCase(value) + suffix;
    }

    private static boolean isAllowedPunctuation(int codePoint) {
        return codePoint == ' ' || codePoint == '\'' || codePoint == '’'
                || codePoint == '.' || codePoint == '-';
    }

    /**
     * Upper-cases the first letter of each word (after a space, hyphen, apostrophe or
     * period) and lower-cases the rest. Locale.ROOT avoids the "Turkish i" problem.
     */
    public static String toTitleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean startOfWord = true;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                int converted = startOfWord ? Character.toUpperCase(codePoint) : Character.toLowerCase(codePoint);
                out.appendCodePoint(converted);
                startOfWord = false;
            } else {
                out.appendCodePoint(codePoint);
                startOfWord = codePoint == ' ' || codePoint == '-' || codePoint == '\''
                        || codePoint == '’' || codePoint == '.';
            }
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }

    /** Converts 1-3999 to Roman numerals (only 1-39 are used for name suffixes). */
    public static String toRoman(int number) {
        if (number < 1 || number > 3999) {
            throw new IllegalArgumentException("Roman numerals support 1 to 3999.");
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                out.append(symbols[i]);
                remaining -= values[i];
            }
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }
}
