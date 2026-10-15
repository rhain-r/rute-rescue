package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureMatch;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spatial index of bridge and tunnel geometry (from OpenStreetMap) used to classify each
 * route sample as SURFACE, BRIDGE, TUNNEL or UNKNOWN (DOCX section 8).
 *
 * Matching rule:
 *  - the sample must be within toleranceMeters of a structure segment, AND
 *  - the route must run in the same direction as the structure (within alignedDegrees).
 * A road that passes UNDER a flyover is close to it but crosses it at a large angle, so it
 * stays SURFACE. A nearby structure at an in-between angle is UNKNOWN, which never shows
 * as green.
 */
public final class StructureIndex implements StructureLookup {

    public static final double DEFAULT_TOLERANCE_METERS = 12.0;
    public static final double DEFAULT_ALIGNED_DEGREES = 30.0;
    public static final double DEFAULT_AMBIGUOUS_DEGREES = 60.0;

    /** Bucket size of the simple grid index, about 110 m. */
    private static final double BUCKET_DEG = 0.001;

    private final Map<String, RoadStructureRecord> recordsById = new LinkedHashMap<>();
    private final Map<Long, List<Segment>> buckets = new HashMap<>();
    private final String datasetVersion;
    private final double toleranceMeters;
    private final double alignedDegrees;
    private final double ambiguousDegrees;

    public StructureIndex(Collection<RoadStructureRecord> records, String datasetVersion) {
        this(records, datasetVersion, DEFAULT_TOLERANCE_METERS, DEFAULT_ALIGNED_DEGREES, DEFAULT_AMBIGUOUS_DEGREES);
    }

    public StructureIndex(Collection<RoadStructureRecord> records, String datasetVersion,
                          double toleranceMeters, double alignedDegrees, double ambiguousDegrees) {
        this.datasetVersion = datasetVersion == null ? "" : datasetVersion;
        this.toleranceMeters = toleranceMeters;
        this.alignedDegrees = alignedDegrees;
        this.ambiguousDegrees = ambiguousDegrees;
        for (RoadStructureRecord record : records) {
            recordsById.put(record.getId(), record);
            List<GeoPoint> line = record.getGeometry();
            for (int i = 0; i + 1 < line.size(); i++) {
                addSegment(new Segment(record, line.get(i), line.get(i + 1)));
            }
        }
    }

    private void addSegment(Segment segment) {
        double padLat = GeoMath.metersToLatDegrees(toleranceMeters);
        double padLon = GeoMath.metersToLonDegrees(toleranceMeters, segment.a.getLatitude());
        int minX = bucket(Math.min(segment.a.getLongitude(), segment.b.getLongitude()) - padLon);
        int maxX = bucket(Math.max(segment.a.getLongitude(), segment.b.getLongitude()) + padLon);
        int minY = bucket(Math.min(segment.a.getLatitude(), segment.b.getLatitude()) - padLat);
        int maxY = bucket(Math.max(segment.a.getLatitude(), segment.b.getLatitude()) + padLat);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(segment);
            }
        }
    }

    @Override
    public StructureMatch match(GeoPoint point, double travelBearingDegrees) {
        List<Segment> candidates = buckets.get(key(bucket(point.getLongitude()), bucket(point.getLatitude())));
        if (candidates == null) {
            return StructureMatch.surface();
        }
        Segment bestAligned = null;
        double bestAlignedDistance = Double.MAX_VALUE;
        Segment ambiguous = null;
        double ambiguousDistance = Double.MAX_VALUE;

        for (Segment segment : candidates) {
            double distance = GeoMath.pointToSegmentMeters(point, segment.a, segment.b);
            if (distance > toleranceMeters) continue;
            double angle = Double.isNaN(travelBearingDegrees)
                    ? 45.0 // unknown travel direction -> cannot confirm alignment
                    : GeoMath.axisDifferenceDegrees(travelBearingDegrees, segment.bearing);
            if (angle <= alignedDegrees) {
                if (distance < bestAlignedDistance) {
                    bestAligned = segment;
                    bestAlignedDistance = distance;
                }
            } else if (angle <= ambiguousDegrees && distance < ambiguousDistance) {
                ambiguous = segment;
                ambiguousDistance = distance;
            }
        }
        if (bestAligned != null) {
            return new StructureMatch(bestAligned.record.getType(), bestAligned.record, bestAlignedDistance);
        }
        if (ambiguous != null) {
            return StructureMatch.unknown(ambiguous.record, ambiguousDistance);
        }
        return StructureMatch.surface();
    }

    /** Applies an LGU status update (from Firestore) to the matching structure. */
    public void applyStatus(String structureId, StructureStatus status, String evidence, long updatedAt, String updatedBy) {
        RoadStructureRecord record = recordsById.get(structureId);
        if (record != null) {
            record.applyStatus(status, evidence, updatedAt, updatedBy);
        }
    }

    /** Nearest structure to a point within maxMeters (used when LGU staff tap the map). */
    public RoadStructureRecord nearest(GeoPoint point, double maxMeters) {
        RoadStructureRecord best = null;
        double bestDistance = maxMeters;
        for (RoadStructureRecord record : recordsById.values()) {
            List<GeoPoint> line = record.getGeometry();
            for (int i = 0; i + 1 < line.size(); i++) {
                double distance = GeoMath.pointToSegmentMeters(point, line.get(i), line.get(i + 1));
                if (distance <= bestDistance) {
                    best = record;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public RoadStructureRecord findById(String id) {
        return recordsById.get(id);
    }

    /**
     * All OpenStreetMap ways that belong to the same physical bridge or tunnel as {@code record}.
     *
     * OpenStreetMap often draws one bridge as several parallel ways: one per direction of a dual
     * carriageway, plus sidewalks. When the LGU closes "the bridge", every part must close, or a
     * route in the other direction would still treat it as merely unverified. A part counts when
     * it has the same structure type, runs roughly parallel (within maxAngleDegrees, either
     * direction), is at least {@link #MIN_PART_METERS} long (so stair fragments are ignored) and
     * its middle lies within maxMeters of the selected way (or the other way round).
     */
    public List<RoadStructureRecord> partsOfSameStructure(RoadStructureRecord record, double maxMeters,
                                                          double maxAngleDegrees) {
        List<RoadStructureRecord> parts = new ArrayList<>();
        if (record == null) return parts;
        parts.add(record);
        double axis = overallBearing(record);
        GeoPoint middle = middleOf(record);
        for (RoadStructureRecord other : recordsById.values()) {
            if (other == record || other.getType() != record.getType() || lengthOf(other) < MIN_PART_METERS) continue;
            if (GeoMath.axisDifferenceDegrees(axis, overallBearing(other)) > maxAngleDegrees) continue;
            if (distanceToLine(middleOf(other), record) <= maxMeters || distanceToLine(middle, other) <= maxMeters) {
                parts.add(other);
            }
        }
        return parts;
    }

    /** Shorter ways (stair pieces, tiny connectors) are never treated as parts of a bridge. */
    public static final double MIN_PART_METERS = 15.0;

    private static double overallBearing(RoadStructureRecord record) {
        List<GeoPoint> line = record.getGeometry();
        return GeoMath.bearingDegrees(line.get(0), line.get(line.size() - 1));
    }

    private static GeoPoint middleOf(RoadStructureRecord record) {
        List<GeoPoint> line = record.getGeometry();
        return GeoMath.interpolate(line.get(0), line.get(line.size() - 1), 0.5);
    }

    private static double lengthOf(RoadStructureRecord record) {
        List<GeoPoint> line = record.getGeometry();
        double total = 0;
        for (int i = 0; i + 1 < line.size(); i++) total += GeoMath.distanceMeters(line.get(i), line.get(i + 1));
        return total;
    }

    private static double distanceToLine(GeoPoint point, RoadStructureRecord record) {
        List<GeoPoint> line = record.getGeometry();
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < line.size(); i++) {
            best = Math.min(best, GeoMath.pointToSegmentMeters(point, line.get(i), line.get(i + 1)));
        }
        return best;
    }

    public Collection<RoadStructureRecord> getRecords() {
        return Collections.unmodifiableCollection(recordsById.values());
    }

    public int size() {
        return recordsById.size();
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    private static int bucket(double degrees) {
        return (int) Math.floor(degrees / BUCKET_DEG);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /** One straight piece of a structure, with its precomputed direction. */
    private static final class Segment {
        final RoadStructureRecord record;
        final GeoPoint a;
        final GeoPoint b;
        final double bearing;

        Segment(RoadStructureRecord record, GeoPoint a, GeoPoint b) {
            this.record = record;
            this.a = a;
            this.b = b;
            this.bearing = GeoMath.bearingDegrees(a, b);
        }
    }
}
