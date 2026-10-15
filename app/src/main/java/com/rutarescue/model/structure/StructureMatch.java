package com.rutarescue.model.structure;

/**
 * Result of asking "which road structure is the traveller on here?".
 * The record is null for SURFACE.
 */
public final class StructureMatch {

    private static final StructureMatch SURFACE = new StructureMatch(RoadStructure.SURFACE, null, Double.NaN);

    private final RoadStructure type;
    private final RoadStructureRecord record;
    private final double distanceMeters;

    public StructureMatch(RoadStructure type, RoadStructureRecord record, double distanceMeters) {
        this.type = type;
        this.record = record;
        this.distanceMeters = distanceMeters;
    }

    public static StructureMatch surface() {
        return SURFACE;
    }

    public static StructureMatch unknown(RoadStructureRecord nearby, double distanceMeters) {
        return new StructureMatch(RoadStructure.UNKNOWN, nearby, distanceMeters);
    }

    public RoadStructure getType() { return type; }
    public RoadStructureRecord getRecord() { return record; }
    public double getDistanceMeters() { return distanceMeters; }
}
