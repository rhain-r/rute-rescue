package com.rutarescue.validation;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Field rules for a family evacuation record, ported from Lab 7 so that the Android
 * forms, the legacy file importer and the FamilyRecord constructor all share one path.
 *
 *  Family ID      [A-Za-z]+-[0-9]{4}, stored upper-case (FR-0002)
 *  Head of family letters/spaces/apostrophes/periods/hyphens, Title Case
 *  Contact        09[0-9]{9} after removing spaces and hyphens
 *  Address        required single-line text (deliberately no strict regex)
 *  Center         required single-line text (natural center names are kept)
 *  Family size    1-100
 */
public final class RecordValidator {

    public static final Pattern FAMILY_ID = Pattern.compile("[A-Za-z]+-[0-9]{4}");

    private RecordValidator() {
    }

    public static String familyId(String raw) throws InvalidFamilyRecordException {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            throw new InvalidFamilyRecordException("familyId", "Family ID is required.");
        }
        if (!FAMILY_ID.matcher(value).matches()) {
            throw new InvalidFamilyRecordException("familyId",
                    "Family ID must contain letters, a hyphen, and exactly 4 digits, e.g. FR-0001.");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static String headName(String raw) throws InvalidFamilyRecordException {
        try {
            return NameFormatter.requirePersonName("headName", raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static String personName(String field, String raw) throws InvalidFamilyRecordException {
        try {
            return NameFormatter.requirePersonName(field, raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static String contactNumber(String raw) throws InvalidFamilyRecordException {
        try {
            return PhoneNumber.requireMobile("contactNumber", raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static String address(String raw) throws InvalidFamilyRecordException {
        try {
            return TextRules.requiredText("address", raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static String evacuationCenter(String raw) throws InvalidFamilyRecordException {
        try {
            return TextRules.requiredText("evacuationCenter", raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static String optionalText(String field, String raw) throws InvalidFamilyRecordException {
        try {
            return TextRules.optionalText(field, raw);
        } catch (ValidationException e) {
            throw wrap(e);
        }
    }

    public static int familySize(int size) throws InvalidFamilyRecordException {
        if (size < InputValidator.MIN_FAMILY_SIZE || size > InputValidator.MAX_FAMILY_SIZE) {
            throw new InvalidFamilyRecordException("familySize", "Family size must be between 1 and 100.");
        }
        return size;
    }

    public static int age(int age) throws InvalidFamilyRecordException {
        if (age < 0 || age > InputValidator.MAX_AGE) {
            throw new InvalidFamilyRecordException("age", "Age must be between 0 and 120.");
        }
        return age;
    }

    private static InvalidFamilyRecordException wrap(ValidationException e) {
        return new InvalidFamilyRecordException(e.getField(), e.getMessage());
    }
}
