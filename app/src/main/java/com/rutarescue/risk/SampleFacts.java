package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureMatch;

/**
 * Everything the risk rules need to know about one sampled point. Collecting the facts
 * first (and judging them second) keeps every rule small and easy to unit-test.
 */
public final class SampleFacts {

    private final GeoPoint point;
    private final HazardClass groundHazard;
    private final boolean covered;
    private final StructureMatch structureMatch;
    private final boolean providerTunnel;
    private final RoadClosure closure;
    private final boolean providerClosure;
    private final boolean structureVerifiedOpen;

    public SampleFacts(GeoPoint point, HazardClass groundHazard, boolean covered, StructureMatch structureMatch,
                       boolean providerTunnel, RoadClosure closure, boolean providerClosure,
                       boolean structureVerifiedOpen) {
        this.point = point;
        this.groundHazard = groundHazard == null ? HazardClass.NONE : groundHazard;
        this.covered = covered;
        this.structureMatch = structureMatch == null ? StructureMatch.surface() : structureMatch;
        this.providerTunnel = providerTunnel;
        this.closure = closure;
        this.providerClosure = providerClosure;
        this.structureVerifiedOpen = structureVerifiedOpen;
    }

    /**
     * The structure type used by the rules. The routing provider's own tunnel sections
     * also count as tunnels, even if our prepared dataset does not contain that tunnel.
     */
    public RoadStructure getStructure() {
        if (structureMatch.getType() == RoadStructure.SURFACE && providerTunnel) {
            return RoadStructure.TUNNEL;
        }
        return structureMatch.getType();
    }

    public String structureName() {
        RoadStructureRecord record = structureMatch.getRecord();
        return record == null ? getStructure().getLabel().toLowerCase() : record.getName();
    }

    public GeoPoint getPoint() { return point; }
    public HazardClass getGroundHazard() { return groundHazard; }
    public boolean isCovered() { return covered; }
    public StructureMatch getStructureMatch() { return structureMatch; }
    public RoadStructureRecord getStructureRecord() { return structureMatch.getRecord(); }
    public boolean isProviderTunnel() { return providerTunnel; }
    public RoadClosure getClosure() { return closure; }
    public boolean isProviderClosure() { return providerClosure; }
    /** True only with fresh verified-open evidence from LGU staff. */
    public boolean isStructureVerifiedOpen() { return structureVerifiedOpen; }
}
