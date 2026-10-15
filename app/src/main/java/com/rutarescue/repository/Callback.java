package com.rutarescue.repository;

/**
 * Result of an asynchronous operation (Firebase calls and background work finish later).
 * Event-driven style: the caller supplies what to do on success and on error.
 *
 * @param <T> type of the successful result
 */
public interface Callback<T> {

    void onSuccess(T result);

    void onError(Exception error);
}
