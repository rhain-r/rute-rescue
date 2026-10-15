package com.rutarescue.exception;

import java.io.IOException;

/**
 * A prepared data file (hazard grid, structure dataset, manifest) does not have the
 * expected format. Thrown instead of returning half-read data.
 */
public class MalformedDataException extends IOException {

    public MalformedDataException(String message) {
        super(message);
    }

    public MalformedDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
