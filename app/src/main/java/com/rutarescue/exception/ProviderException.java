package com.rutarescue.exception;

/**
 * An external provider (TomTom) answered with an error or with data we could not read.
 */
public class ProviderException extends Exception {

    private final int httpStatus;

    public ProviderException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ProviderException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status code, or -1 when the problem was not an HTTP status. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
