package com.rutarescue.model.common;

/**
 * Anything that can be used as a map destination: an evacuation center, a saved place
 * or a search result.
 *
 * Polymorphism: the Safe Route screen only needs a Locatable, so it can route to any of
 * these different classes without knowing which one it received.
 */
public interface Locatable {

    GeoPoint getLocation();

    String getDisplayName();

    /** A short second line (address, status, etc.) shown under the name. */
    String getSubtitle();
}
