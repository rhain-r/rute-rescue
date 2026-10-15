package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the closed stretches of assessed routes (an LGU-closed bridge, a reported road closure)
 * into rectangles the routing provider should avoid, so RouteService can ask for detours when
 * every provider route crosses a closure. Pure Java, so it is unit-tested without Android.
 */
public final class ClosureAvoidance {

    /** TomTom accepts at most 10 avoid rectangles per request. */
    public static final int MAX_AREAS = 10;

    private ClosureAvoidance() {
    }

    /** True when there is at least one route and every route crosses a closure. */
    public static boolean allClosed(List<RouteAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) return false;
        for (RouteAssessment assessment : assessments) {
            if (!assessment.isClosed()) return false;
        }
        return true;
    }

    /**
     * One rectangle per continuous closed stretch of every route, grown by paddingMeters.
     * Overlapping rectangles are merged, and at most {@link #MAX_AREAS} are returned.
     */
    public static List<AvoidArea> areasToAvoid(List<RouteAssessment> assessments, double paddingMeters) {
        List<AvoidArea> areas = new ArrayList<>();
        for (RouteAssessment assessment : assessments) {
            List<GeoPoint> stretch = new ArrayList<>();
            for (RouteSample sample : assessment.getSamples()) {
                if (sample.getVerdict().isClosed()) {
                    stretch.add(sample.getPoint());
                } else if (!stretch.isEmpty()) {
                    add(areas, AvoidArea.around(stretch, paddingMeters));
                    stretch = new ArrayList<>();
                }
            }
            if (!stretch.isEmpty()) add(areas, AvoidArea.around(stretch, paddingMeters));
        }
        return areas.size() > MAX_AREAS ? new ArrayList<>(areas.subList(0, MAX_AREAS)) : areas;
    }

    /** Adds the area, merging it with any rectangle it overlaps. */
    private static void add(List<AvoidArea> areas, AvoidArea area) {
        AvoidArea merged = area;
        for (int i = areas.size() - 1; i >= 0; i--) {
            if (areas.get(i).overlaps(merged)) {
                merged = merged.union(areas.remove(i));
            }
        }
        areas.add(merged);
    }
}
