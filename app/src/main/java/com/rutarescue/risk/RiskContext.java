package com.rutarescue.risk;

import com.rutarescue.model.hazard.FloodScenario;

/**
 * The data a route assessment depends on: the selected scenario's hazard lookup, the
 * road-structure index, active closures, and the current time (so "fresh" evidence can
 * be judged and tests can use a fixed clock).
 */
public final class RiskContext {

    /** Default sampling step: 10 metres. */
    public static final double DEFAULT_STEP_METERS = 10.0;
    /** Verified-open evidence older than this is no longer "fresh" (24 hours). */
    public static final long DEFAULT_VERIFIED_OPEN_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final HazardLookup hazard;
    private final StructureLookup structures;
    private final ClosureLookup closures;
    private final long now;
    private final double stepMeters;
    private final long verifiedOpenMaxAgeMs;

    public RiskContext(HazardLookup hazard, StructureLookup structures, ClosureLookup closures, long now) {
        this(hazard, structures, closures, now, DEFAULT_STEP_METERS, DEFAULT_VERIFIED_OPEN_MAX_AGE_MS);
    }

    public RiskContext(HazardLookup hazard, StructureLookup structures, ClosureLookup closures, long now,
                       double stepMeters, long verifiedOpenMaxAgeMs) {
        if (hazard == null) throw new IllegalArgumentException("A hazard lookup is required.");
        this.hazard = hazard;
        this.structures = structures == null ? StructureLookup.NONE : structures;
        this.closures = closures == null ? ClosureLookup.NONE : closures;
        this.now = now;
        this.stepMeters = stepMeters;
        this.verifiedOpenMaxAgeMs = verifiedOpenMaxAgeMs;
    }

    public FloodScenario getScenario() { return hazard.getScenario(); }
    public HazardLookup getHazard() { return hazard; }
    public StructureLookup getStructures() { return structures; }
    public ClosureLookup getClosures() { return closures; }
    public long getNow() { return now; }
    public double getStepMeters() { return stepMeters; }
    public long getVerifiedOpenMaxAgeMs() { return verifiedOpenMaxAgeMs; }
}
