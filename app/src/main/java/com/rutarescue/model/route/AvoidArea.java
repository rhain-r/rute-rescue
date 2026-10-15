package com.rutarescue.model.route;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.util.GeoMath;

import java.util.List;

/**
 * A rectangle the routing provider must avoid, e.g. around a bridge the LGU closed.
 * TomTom's Routing API accepts up to 10 such rectangles ("avoidAreas").
 * Encapsulation: corners are validated once and cannot change afterwards.
 */
public final class AvoidArea {

    private final GeoPoint southWest;
    private final GeoPoint northEast;

    public AvoidArea(GeoPoint southWest, GeoPoint northEast) {
        if (southWest == null || northEast == null) throw new IllegalArgumentException("Both corners are required.");
        if (southWest.getLatitude() > northEast.getLatitude() || southWest.getLongitude() > northEast.getLongitude()) {
            throw new IllegalArgumentException("The south-west corner must be below and left of the north-east corner.");
        }
        this.southWest = southWest;
        this.northEast = northEast;
    }

    /** The smallest rectangle around the points, grown by paddingMeters on every side. */
    public static AvoidArea around(List<GeoPoint> points, double paddingMeters) {
        if (points == null || points.isEmpty()) throw new IllegalArgumentException("At least one point is required.");
        double minLat = Double.MAX_VALUE, minLon = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (GeoPoint point : points) {
            minLat = Math.min(minLat, point.getLatitude());
            maxLat = Math.max(maxLat, point.getLatitude());
            minLon = Math.min(minLon, point.getLongitude());
            maxLon = Math.max(maxLon, point.getLongitude());
        }
        double padLat = GeoMath.metersToLatDegrees(paddingMeters);
        double padLon = GeoMath.metersToLonDegrees(paddingMeters, (minLat + maxLat) / 2);
        return new AvoidArea(new GeoPoint(minLat - padLat, minLon - padLon), new GeoPoint(maxLat + padLat, maxLon + padLon));
    }

    public boolean overlaps(AvoidArea other) {
        return southWest.getLatitude() <= other.northEast.getLatitude() && other.southWest.getLatitude() <= northEast.getLatitude()
                && southWest.getLongitude() <= other.northEast.getLongitude() && other.southWest.getLongitude() <= northEast.getLongitude();
    }

    /** The smallest rectangle covering both areas. */
    public AvoidArea union(AvoidArea other) {
        return new AvoidArea(
                new GeoPoint(Math.min(southWest.getLatitude(), other.southWest.getLatitude()),
                        Math.min(southWest.getLongitude(), other.southWest.getLongitude())),
                new GeoPoint(Math.max(northEast.getLatitude(), other.northEast.getLatitude()),
                        Math.max(northEast.getLongitude(), other.northEast.getLongitude())));
    }

    public GeoPoint getSouthWest() { return southWest; }
    public GeoPoint getNorthEast() { return northEast; }
}
