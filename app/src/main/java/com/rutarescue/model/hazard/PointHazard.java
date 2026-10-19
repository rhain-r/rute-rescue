package com.rutarescue.model.hazard;

import com.rutarescue.model.common.GeoPoint;

/**
 * The answer to "which NOAH hazard class is mapped at this point, in this scenario?".
 * Immutable, so a background thread can build it and hand it to the UI thread safely.
 */
public final class PointHazard {

    private final GeoPoint point;
    private final FloodScenario scenario;
    private final boolean covered;
    private final HazardClass hazardClass;

    public PointHazard(GeoPoint point, FloodScenario scenario, boolean covered, HazardClass hazardClass) {
        if (point == null || scenario == null) throw new IllegalArgumentException("Point and scenario are required.");
        this.point = point;
        this.scenario = scenario;
        this.covered = covered;
        this.hazardClass = covered && hazardClass != null ? hazardClass : HazardClass.NONE;
    }

    public GeoPoint getPoint() { return point; }
    public FloodScenario getScenario() { return scenario; }
    /** False when the point is outside the area the prepared NOAH data covers. */
    public boolean isCovered() { return covered; }
    public HazardClass getHazardClass() { return hazardClass; }

    /** Text for the LGU map's selected-point card, e.g. "NOAH 5-year: High (over 1.5 m)". */
    public String describe() {
        if (!covered) return "Outside prepared NOAH coverage";
        return "NOAH " + scenario.getLabel() + ": " + hazardClass.getLabel() + " (" + hazardClass.getDepthRange() + ")";
    }
}
