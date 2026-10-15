package com.rutarescue.repository;

/** Returned by "listen" methods; call remove() when the screen no longer needs updates. */
public interface ListenerHandle {

    void remove();

    ListenerHandle NONE = () -> { };
}
