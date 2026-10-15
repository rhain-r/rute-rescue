package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;

/**
 * Decides, during guidance, whether a fresh route from the resident's position should quietly
 * replace the rest of the current route (Phase 1 "Guidance with quiet rerouting").
 *
 * Only the part still ahead counts: hazards already passed are ignored. The new route must
 * clearly improve: a lower worst hazard, at least 50 m less flooded road, or (same risk) live
 * traffic that saves at least 3 minutes. A closed route is never chosen.
 */
public final class RerouteAdvisor {

    /** Why the route was replaced; NONE keeps the current route. */
    public enum Reason {
        NONE,
        LOWER_RISK,
        LESS_TRAFFIC
    }

    static final double MIN_EXPOSURE_SAVING_METERS = 50;
    static final long MIN_TIME_SAVING_SECONDS = 180;

    /**
     * @param current     the route being followed
     * @param alongMeters how far along the current route the resident already is
     * @param fresh       the best route found from the resident's position now
     */
    public Reason compare(RouteAssessment current, double alongMeters, RouteAssessment fresh) {
        if (fresh.isClosed()) return Reason.NONE;

        HazardClass aheadWorst = HazardClass.NONE;
        double aheadExposure = 0;
        for (RouteSample sample : current.getSamples()) {
            if (sample.getDistanceAlongMeters() < alongMeters) continue;
            HazardClass hazard = sample.getVerdict().getEffectiveHazard();
            aheadWorst = HazardClass.max(aheadWorst, hazard);
            if (hazard != HazardClass.NONE) aheadExposure += sample.getLengthMeters();
        }

        if (current.isClosed() || fresh.getMaxEffectiveHazard().getCode() < aheadWorst.getCode()) {
            return Reason.LOWER_RISK;
        }
        if (fresh.getMaxEffectiveHazard().getCode() > aheadWorst.getCode()) return Reason.NONE;
        if (fresh.getTotalExposureMeters() + MIN_EXPOSURE_SAVING_METERS <= aheadExposure) return Reason.LOWER_RISK;

        double length = current.getCandidate().getLengthMeters();
        double remainingShare = length <= 0 ? 0 : Math.max(0, length - alongMeters) / length;
        double remainingSeconds = current.getCandidate().getTravelTimeSeconds() * remainingShare;
        boolean sameRiskOrLess = fresh.getTotalExposureMeters() <= aheadExposure + MIN_EXPOSURE_SAVING_METERS;
        if (fresh.getCandidate().isTrafficConsidered() && sameRiskOrLess
                && fresh.getCandidate().getTravelTimeSeconds() + MIN_TIME_SAVING_SECONDS <= remainingSeconds) {
            return Reason.LESS_TRAFFIC;
        }
        return Reason.NONE;
    }
}
