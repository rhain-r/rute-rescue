package com.rutarescue.model.route;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.validation.TextRules;

/**
 * A confirmed closure or impassable spot reported by LGU field staff. Closures are kept
 * separate from the NOAH layers: they are field evidence, not modelled hazard.
 * A route passing through an active closure is ranked last (DOCX section 8, rule 1).
 */
public class RoadClosure extends AuditableEntity implements Locatable {

    public static final int MIN_RADIUS_METERS = 10;
    public static final int MAX_RADIUS_METERS = 1000;

    private final String name;
    private final GeoPoint location;
    private final int radiusMeters;
    private final String reason;
    private final MetroManilaCity city;
    private boolean active;
    private final long reportedAt;

    public RoadClosure(String id, String name, GeoPoint location, int radiusMeters, String reason,
                       MetroManilaCity city, boolean active, long reportedAt) throws ValidationException {
        super(id, null, false);
        this.name = TextRules.requiredText("closureName", name);
        if (location == null || !location.isInsideMetroManilaRegion()) {
            throw new ValidationException("location", "The closure must be inside Metro Manila.");
        }
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new ValidationException("radius", "Radius must be between 10 and 1000 metres.");
        }
        if (city == null) {
            throw new ValidationException("city", "A closure needs the reporting city.");
        }
        this.location = location;
        this.radiusMeters = radiusMeters;
        this.reason = TextRules.requiredText("reason", reason);
        this.city = city;
        this.active = active;
        this.reportedAt = reportedAt;
    }

    @Override
    public String getEntityType() {
        return "closure";
    }

    public void lift(String actorUid, long now) {
        active = false;
        markUpdated(actorUid, now);
    }

    @Override
    public GeoPoint getLocation() { return location; }

    @Override
    public String getDisplayName() { return name; }

    @Override
    public String getSubtitle() { return reason; }

    public String getName() { return name; }
    public int getRadiusMeters() { return radiusMeters; }
    public String getReason() { return reason; }
    public MetroManilaCity getCity() { return city; }
    public boolean isActive() { return active; }
    public long getReportedAt() { return reportedAt; }
}
