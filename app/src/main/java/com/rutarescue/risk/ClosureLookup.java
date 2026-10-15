package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.RoadClosure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds an active LGU-reported closure covering a point. Implemented as a small class
 * with a static factory because the number of active closures is always small.
 */
public interface ClosureLookup {

    /** The closure covering the point, or null if none. */
    RoadClosure findClosure(GeoPoint point);

    ClosureLookup NONE = point -> null;

    /** Simple implementation: checks every active closure's radius. */
    static ClosureLookup of(Collection<RoadClosure> closures) {
        final List<RoadClosure> active = new ArrayList<>();
        for (RoadClosure closure : closures) {
            if (closure.isActive() && !closure.isArchived()) active.add(closure);
        }
        return point -> {
            for (RoadClosure closure : active) {
                double distance = com.rutarescue.util.GeoMath.distanceMeters(point, closure.getLocation());
                if (distance <= closure.getRadiusMeters()) return closure;
            }
            return null;
        };
    }
}
