package com.rutarescue.exception;

/**
 * A feature needs the internet (for example TomTom search or a new route) but the phone
 * is offline. The UI turns this into an explanation instead of a crash (DOCX section 11).
 */
public class OfflineException extends Exception {

    public OfflineException(String message) {
        super(message);
    }

    public OfflineException(String message, Throwable cause) {
        super(message, cause);
    }
}
