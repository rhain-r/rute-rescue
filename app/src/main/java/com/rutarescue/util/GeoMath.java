package com.rutarescue.util;

import com.rutarescue.model.common.GeoPoint;

/**
 * Small geometry helpers. distanceMeters() and bearingDegrees() are ported from the
 * legacy Jabbacodeez utils/distance.js and utils/bearing.js.
 */
public final class GeoMath {

    public static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = 111_132.0;
    private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0;

    private GeoMath() {
    }

    /** Haversine great-circle distance in metres. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static double distanceMeters(GeoPoint a, GeoPoint b) {
        return distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    /** Initial compass bearing from one point to another, 0-360 degrees. */
    public static double bearingDegrees(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    /**
     * Angle between two road directions ignoring which way you drive along them (0-90).
     * A road and the same road driven backwards give 0; perpendicular roads give 90.
     */
    public static double axisDifferenceDegrees(double bearingA, double bearingB) {
        double difference = Math.abs(bearingA - bearingB) % 180.0;
        return difference > 90.0 ? 180.0 - difference : difference;
    }

    /** Linear interpolation between two nearby points (fraction 0..1). */
    public static GeoPoint interpolate(GeoPoint a, GeoPoint b, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return new GeoPoint(
                a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f,
                a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f);
    }

    /**
     * Shortest distance in metres from point p to segment a-b. Uses a local flat
     * projection, which is accurate enough for the few-metre tolerances used here.
     */
    public static double pointToSegmentMeters(GeoPoint p, GeoPoint a, GeoPoint b) {
        double metersPerLon = METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(Math.toRadians(p.getLatitude()));
        double ax = (a.getLongitude() - p.getLongitude()) * metersPerLon;
        double ay = (a.getLatitude() - p.getLatitude()) * METERS_PER_DEGREE_LAT;
        double bx = (b.getLongitude() - p.getLongitude()) * metersPerLon;
        double by = (b.getLatitude() - p.getLatitude()) * METERS_PER_DEGREE_LAT;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.sqrt(cx * cx + cy * cy);
    }

    /** Converts metres to degrees of latitude (used for search-box sizes). */
    public static double metersToLatDegrees(double meters) {
        return meters / METERS_PER_DEGREE_LAT;
    }

    /** Converts metres to degrees of longitude at the given latitude. */
    public static double metersToLonDegrees(double meters, double latitude) {
        return meters / (METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(Math.toRadians(latitude)));
    }
}
