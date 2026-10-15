package com.rutarescue.exception;

import java.io.IOException;

/**
 * A save to disk failed. Because files are written to a temporary sibling first, the
 * previously saved file is still intact and the in-memory records are untouched, so the
 * user can simply try again later (Lab 6/7 "keep memory values on I/O error").
 */
public class AtomicSaveException extends IOException {

    public AtomicSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
