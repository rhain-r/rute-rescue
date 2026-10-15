package com.rutarescue.model.structure;

import com.rutarescue.model.common.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A bridge or tunnel from the prepared road-structure dataset (OpenStreetMap), plus the
 * latest passability status recorded by LGU staff. The geometry is versioned separately
 * from the NOAH layers (DOCX section 8).
 */
public final class RoadStructureRecord {

    private final String id;
    private final RoadStructure type;
    private final String name;
    private final int layer;
    private final List<GeoPoint> geometry;
    private StructureStatus status = StructureStatus.UNVERIFIED;
    private String evidence = "";
    private long statusUpdatedAt;
    private String statusUpdatedBy = "";

    public RoadStructureRecord(String id, RoadStructure type, String name, int layer, List<GeoPoint> geometry) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Structure id is required.");
        if (type != RoadStructure.BRIDGE && type != RoadStructure.TUNNEL) {
            throw new IllegalArgumentException("Only bridges and tunnels are stored as structures.");
        }
        if (geometry == null || geometry.size() < 2) {
            throw new IllegalArgumentException("A structure needs at least two points.");
        }
        this.id = id;
        this.type = type;
        this.name = name == null || name.isBlank() ? "Unnamed " + type.getLabel().toLowerCase() : name.strip();
        this.layer = layer;
        this.geometry = Collections.unmodifiableList(new ArrayList<>(geometry));
    }

    /** Applies the latest LGU status (called when status records sync from Firestore). */
    public void applyStatus(StructureStatus newStatus, String newEvidence, long updatedAt, String updatedBy) {
        status = newStatus == null ? StructureStatus.UNVERIFIED : newStatus;
        evidence = newEvidence == null ? "" : newEvidence;
        statusUpdatedAt = updatedAt;
        statusUpdatedBy = updatedBy == null ? "" : updatedBy;
    }

    /** "Fresh verified-open evidence" (DOCX section 8): verified open and not too old. */
    public boolean isVerifiedOpen(long now, long maxAgeMillis) {
        return status == StructureStatus.VERIFIED_OPEN && now - statusUpdatedAt <= maxAgeMillis;
    }

    public boolean isClosed() {
        return status == StructureStatus.CLOSED;
    }

    public GeoPoint getMidpoint() {
        return geometry.get(geometry.size() / 2);
    }

    public String getId() { return id; }
    public RoadStructure getType() { return type; }
    public String getName() { return name; }
    public int getLayer() { return layer; }
    public List<GeoPoint> getGeometry() { return geometry; }
    public StructureStatus getStatus() { return status; }
    public String getEvidence() { return evidence; }
    public long getStatusUpdatedAt() { return statusUpdatedAt; }
    public String getStatusUpdatedBy() { return statusUpdatedBy; }
}
