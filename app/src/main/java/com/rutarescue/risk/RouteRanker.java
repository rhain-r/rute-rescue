package com.rutarescue.risk;

import com.rutarescue.model.route.RouteAssessment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks route alternatives lexicographically (DOCX section 8):
 *   1. explicit closure or confirmed impassability (open routes first)
 *   2. maximum applicable hazard severity
 *   3. total distance exposed to flood hazard
 *   4. total distance on unverified bridges/structures
 *   5. travel time (live, or clearly labelled provider/cached time)
 * A later criterion is only used when all earlier ones are equal, so no time saving can
 * outrank a confirmed closure or a more severe hazard.
 *
 * Implements Comparator, so it plugs straight into List.sort().
 */
public final class RouteRanker implements Comparator<RouteAssessment> {

    /** Distances are compared to the nearest metre to avoid floating-point noise. */
    @Override
    public int compare(RouteAssessment a, RouteAssessment b) {
        int result = Boolean.compare(a.isClosed(), b.isClosed());
        if (result != 0) return result;
        result = Integer.compare(a.getMaxEffectiveHazard().getCode(), b.getMaxEffectiveHazard().getCode());
        if (result != 0) return result;
        result = Long.compare(Math.round(a.getTotalExposureMeters()), Math.round(b.getTotalExposureMeters()));
        if (result != 0) return result;
        result = Long.compare(Math.round(a.getUnverifiedStructureMeters()), Math.round(b.getUnverifiedStructureMeters()));
        if (result != 0) return result;
        return Long.compare(a.getCandidate().getTravelTimeSeconds(), b.getCandidate().getTravelTimeSeconds());
    }

    /** Returns a new sorted list and stores each route's rank (1 = first). */
    public List<RouteAssessment> rank(List<RouteAssessment> assessments) {
        List<RouteAssessment> sorted = new ArrayList<>(assessments);
        sorted.sort(this);
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1, sorted.size());
        }
        return sorted;
    }
}
