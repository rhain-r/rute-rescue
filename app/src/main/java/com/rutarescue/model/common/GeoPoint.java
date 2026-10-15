package com.rutarescue.model.common;

import java.util.Locale;

/**
 * An immutable latitude/longitude pair in WGS84 (EPSG:4326), the same coordinate
 * system used by the Project NOAH shapefiles.
 *
 * Encapsulation: the fields are private and final, and the constructor rejects
 * impossible coordinates, so a GeoPoint object is always valid.
 */
public final class GeoPoint {

    private final double latitude;
    private final double longitude;

    public GeoPoint(double latitude, double longitude) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** True when the point lies inside a generous box around Metro Manila. */
    public boolean isInsideMetroManilaRegion() {
        return latitude >= 14.25 && latitude <= 14.90 && longitude >= 120.80 && longitude <= 121.25;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GeoPoint)) return false;
        GeoPoint that = (GeoPoint) other;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(latitude) * 31 + Double.doubleToLongBits(longitude);
        return (int) (bits ^ (bits >>> 32));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
    }
}
