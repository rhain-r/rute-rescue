package com.rutarescue.persistence;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.family.TrustedContact;
import com.rutarescue.validation.InputValidator;
import com.rutarescue.validation.PhoneNumber;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between a FamilyRecord and one line of family_records.txt.
 *
 * Current format (Lab 6/7, six fields, UTF-8, pipe-delimited):
 *     FR-0002|Maria O'Neill-Santos|09171234567|12 Sample St., Brgy. Malanday|School Gym|4
 *     ID     |head of family      |contact    |address                      |center    |size
 *
 * Lab 5 rows had seven fields (ID|name|address|contact|size|emergency contact|center).
 * They are still accepted on import; the emergency contact becomes a trusted contact.
 */
public final class LegacyFamilyCodec {

    public static final String DELIMITER = "|";
    public static final int CURRENT_FIELD_COUNT = 6;
    public static final int LAB5_FIELD_COUNT = 7;

    /** Finds a mobile number written inside free text, e.g. "Juan (0917-123-4567)". */
    private static final Pattern EMBEDDED_MOBILE = Pattern.compile("09[0-9 -]{9,13}");

    private LegacyFamilyCodec() {
    }

    public static String toLine(FamilyRecord record) {
        return record.toFileString();
    }

    /**
     * Parses one saved row.
     *
     * @throws NumberFormatException       when the family size is not a whole number (Lab 6 path)
     * @throws InvalidFamilyRecordException when any other rule is broken (custom exception)
     */
    public static FamilyRecord parseLine(String line) throws InvalidFamilyRecordException {
        // -1 keeps trailing empty fields, so "a|b|" is reported as having 3 fields.
        String[] fields = line.split("\\|", -1);
        if (fields.length == CURRENT_FIELD_COUNT) {
            return new FamilyRecord(fields[0], fields[1], fields[2], fields[3], fields[4], parseSize(fields[5]));
        }
        if (fields.length == LAB5_FIELD_COUNT) {
            FamilyRecord record = new FamilyRecord(fields[0], fields[1], fields[3], fields[2], fields[6],
                    parseSize(fields[4]));
            TrustedContact emergency = parseEmergencyContact(fields[5]);
            if (emergency != null) record.addTrustedContact(emergency);
            return record;
        }
        throw new InvalidFamilyRecordException("row", "Expected " + CURRENT_FIELD_COUNT
                + " pipe-separated fields but found " + fields.length + ".");
    }

    private static int parseSize(String raw) throws InvalidFamilyRecordException {
        try {
            return InputValidator.parseFamilySize(raw);
        } catch (NumberFormatException notANumber) {
            throw notANumber; // handled separately by the caller (Lab 6)
        } catch (IllegalArgumentException outOfRange) {
            throw new InvalidFamilyRecordException("familySize", outOfRange.getMessage());
        }
    }

    private static TrustedContact parseEmergencyContact(String raw) throws InvalidFamilyRecordException {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = EMBEDDED_MOBILE.matcher(raw);
        if (matcher.find()) {
            String phone = PhoneNumber.normalize(matcher.group());
            if (PhoneNumber.isValidMobile(phone)) {
                String name = (raw.substring(0, matcher.start()) + raw.substring(matcher.end()))
                        .replace("(", " ").replace(")", " ").strip();
                return new TrustedContact(name, phone);
            }
        }
        return new TrustedContact(raw, null);
    }
}
