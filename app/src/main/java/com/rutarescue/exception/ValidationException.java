package com.rutarescue.exception;

/**
 * Thrown when user-entered or imported data breaks a domain rule (for example a blank
 * name or an invalid phone number). It is a checked exception, so callers must decide
 * how to recover (Lab 6: "throw" where the rule fails, "throws" to let the caller handle it).
 */
public class ValidationException extends Exception {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field == null ? "" : field;
    }

    /** The form field or record column that failed, e.g. "contactNumber". */
    public String getField() {
        return field;
    }
}
