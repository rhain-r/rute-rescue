package com.rutarescue.model.route;

import com.rutarescue.model.common.GeoPoint;

/**
 * One text instruction (ported from the legacy NavigationSteps component):
 * message, street, location and distance from the route start.
 */
public final class Maneuver {

    private final String message;
    private final String street;
    private final GeoPoint location;
    private final double routeOffsetMeters;

    public Maneuver(String message, String street, GeoPoint location, double routeOffsetMeters) {
        this.message = message == null || message.isBlank() ? "Continue" : message.strip();
        this.street = street == null ? "" : street.strip();
        this.location = location;
        this.routeOffsetMeters = Math.max(0, routeOffsetMeters);
    }

    public String getMessage() { return message; }
    public String getStreet() { return street; }
    public GeoPoint getLocation() { return location; }
    public double getRouteOffsetMeters() { return routeOffsetMeters; }

    /** "Turn left onto Mabini Street" style text. */
    public String describe() {
        if (street.isEmpty() || message.toLowerCase().contains(street.toLowerCase())) {
            return message;
        }
        return message + " (" + street + ")";
    }
}
