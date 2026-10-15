package com.rutarescue.exception;

/**
 * The project-specific custom exception carried over from Lab 6: an evacuation family
 * record violates a required rule (required fields, family ID format, contact-number
 * format, delimiter protection, or a family size outside 1-100).
 *
 * It extends ValidationException because an invalid family record really is a kind of
 * validation failure (meaningful inheritance).
 */
public class InvalidFamilyRecordException extends ValidationException {

    public InvalidFamilyRecordException(String field, String message) {
        super(field, message);
    }

    public InvalidFamilyRecordException(String message) {
        super("record", message);
    }
}
