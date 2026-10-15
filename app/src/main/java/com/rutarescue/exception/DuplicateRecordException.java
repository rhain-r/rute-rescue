package com.rutarescue.exception;

/**
 * Raised when a batch of records contains the same ID twice (Lab 7 duplicate-ID rule,
 * compared case-insensitively). The batch is rejected before any file is touched.
 */
public class DuplicateRecordException extends Exception {

    private final String duplicateId;

    public DuplicateRecordException(String duplicateId) {
        super("Duplicate family ID: " + duplicateId + ". Each family ID must be unique.");
        this.duplicateId = duplicateId;
    }

    public String getDuplicateId() {
        return duplicateId;
    }
}
