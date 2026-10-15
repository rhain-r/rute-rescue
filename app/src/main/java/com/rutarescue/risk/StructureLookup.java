package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.structure.StructureMatch;

/**
 * Answers "is the traveller on a bridge, in a tunnel, on a surface road, or can we not
 * tell?" for a point and the travel direction at that point.
 */
public interface StructureLookup {

    StructureMatch match(GeoPoint point, double travelBearingDegrees);

    /** A lookup with no structures: every point is a surface road (used when no data loaded). */
    StructureLookup NONE = (point, bearing) -> StructureMatch.surface();
}
