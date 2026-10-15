package com.rutarescue.model.route;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.structure.RoadStructure;

/**
 * The result of one risk rule for one sampled point of a route.
 *
 * groundHazard    = the NOAH class on the ground at this point
 * effectiveHazard = the hazard that actually applies to the traveller after the
 *                   structure rules (a verified-open bridge does not inherit the
 *                   ground depth, a flooded tunnel is treated as HIGH, ...)
 */
public final class SampleVerdict {

    private final RiskLevel level;
    private final HazardClass groundHazard;
    private final HazardClass effectiveHazard;
    private final RoadStructure structure;
    private final boolean closed;
    private final boolean unverifiedStructure;
    private final boolean outOfCoverage;
    private final String reason;
    private final String ruleName;

    private SampleVerdict(Builder builder) {
        this.level = builder.level;
        this.groundHazard = builder.groundHazard;
        this.effectiveHazard = builder.effectiveHazard;
        this.structure = builder.structure;
        this.closed = builder.closed;
        this.unverifiedStructure = builder.unverifiedStructure;
        this.outOfCoverage = builder.outOfCoverage;
        this.reason = builder.reason;
        this.ruleName = builder.ruleName;
    }

    public RiskLevel getLevel() { return level; }
    public HazardClass getGroundHazard() { return groundHazard; }
    public HazardClass getEffectiveHazard() { return effectiveHazard; }
    public RoadStructure getStructure() { return structure; }
    public boolean isClosed() { return closed; }
    public boolean isUnverifiedStructure() { return unverifiedStructure; }
    public boolean isOutOfCoverage() { return outOfCoverage; }
    public String getReason() { return reason; }
    public String getRuleName() { return ruleName; }

    public static Builder builder(String ruleName, RiskLevel level, String reason) {
        return new Builder(ruleName, level, reason);
    }

    /** Small builder so each rule only sets the values it cares about. */
    public static final class Builder {
        private final String ruleName;
        private final RiskLevel level;
        private final String reason;
        private HazardClass groundHazard = HazardClass.NONE;
        private HazardClass effectiveHazard = HazardClass.NONE;
        private RoadStructure structure = RoadStructure.SURFACE;
        private boolean closed;
        private boolean unverifiedStructure;
        private boolean outOfCoverage;

        private Builder(String ruleName, RiskLevel level, String reason) {
            this.ruleName = ruleName;
            this.level = level;
            this.reason = reason == null ? "" : reason;
        }

        public Builder ground(HazardClass value) { groundHazard = value; return this; }
        public Builder effective(HazardClass value) { effectiveHazard = value; return this; }
        public Builder structure(RoadStructure value) { structure = value; return this; }
        public Builder closed(boolean value) { closed = value; return this; }
        public Builder unverifiedStructure(boolean value) { unverifiedStructure = value; return this; }
        public Builder outOfCoverage(boolean value) { outOfCoverage = value; return this; }

        public SampleVerdict build() {
            return new SampleVerdict(this);
        }
    }
}
