package com.rutarescue.model.route;

import com.rutarescue.model.common.GeoPoint;

/** One fixed-distance sample of a route together with its verdict. */
public final class RouteSample {

    private final GeoPoint point;
    private final double distanceAlongMeters;
    private final double lengthMeters;
    private final SampleVerdict verdict;

    public RouteSample(GeoPoint point, double distanceAlongMeters, double lengthMeters, SampleVerdict verdict) {
        this.point = point;
        this.distanceAlongMeters = distanceAlongMeters;
        this.lengthMeters = lengthMeters;
        this.verdict = verdict;
    }

    public GeoPoint getPoint() { return point; }
    public double getDistanceAlongMeters() { return distanceAlongMeters; }
    /** How many metres of road this sample represents. */
    public double getLengthMeters() { return lengthMeters; }
    public SampleVerdict getVerdict() { return verdict; }
}
