package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.route.RouteWarning;
import com.rutarescue.model.route.TravelMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds the Phase 1 warning drawer for one assessed route: closures, flood hazard by class
 * (naming the street where it starts), unverified bridges/tunnels, stretches outside the flood
 * map and traffic delay. Serious warnings are listed first.
 */
public final class RouteWarningBuilder {

    /** Delays shorter than this are not worth a warning. */
    static final long MIN_TRAFFIC_DELAY_SECONDS = 120;
    /** From this delay the wording changes from "Slow" to "Heavy". */
    static final long HEAVY_TRAFFIC_SECONDS = 8 * 60;

    /** @param live true when the route times came from a live provider answer, false when cached. */
    public List<RouteWarning> build(RouteAssessment route, TravelMode mode, boolean live) {
        List<RouteWarning> warnings = new ArrayList<>();
        if (route.isClosed()) {
            warnings.add(new RouteWarning(RouteWarning.Kind.CLOSURE, "Crosses a reported closure",
                    "An LGU reported this road closed; pick another route"));
        }
        addFlood(warnings, route, HazardClass.HIGH, RouteWarning.Kind.HIGH_FLOOD);
        addFlood(warnings, route, HazardClass.MEDIUM, RouteWarning.Kind.MEDIUM_FLOOD);
        addFlood(warnings, route, HazardClass.LOW, RouteWarning.Kind.LOW_FLOOD);
        if (route.getUnverifiedStructureMeters() > 0) {
            warnings.add(new RouteWarning(RouteWarning.Kind.UNVERIFIED_STRUCTURE, "Unverified bridge or tunnel",
                    meters(route.getUnverifiedStructureMeters()) + " on structures the LGU has not confirmed"));
        }
        if (route.getOutOfCoverageMeters() > 0) {
            warnings.add(new RouteWarning(RouteWarning.Kind.OUTSIDE_FLOOD_MAP, "Outside the flood map",
                    meters(route.getOutOfCoverageMeters()) + " has no prepared NOAH data"));
        }
        long delay = route.getCandidate().getTrafficDelaySeconds();
        if (mode.usesTraffic() && route.getCandidate().isTrafficConsidered() && delay >= MIN_TRAFFIC_DELAY_SECONDS) {
            long minutes = Math.round(delay / 60.0);
            warnings.add(new RouteWarning(RouteWarning.Kind.TRAFFIC,
                    (delay >= HEAVY_TRAFFIC_SECONDS ? "Heavy" : "Slow") + " traffic on this route",
                    "About " + minutes + " minutes of delay · " + (live ? "live" : "cached")));
        }
        warnings.sort(Comparator.comparingInt(warning -> warning.getKind().ordinal()));
        return warnings;
    }

    public static boolean hasSerious(List<RouteWarning> warnings) {
        for (RouteWarning warning : warnings) {
            if (warning.isSerious()) return true;
        }
        return false;
    }

    private static void addFlood(List<RouteWarning> warnings, RouteAssessment route, HazardClass hazardClass,
                                 RouteWarning.Kind kind) {
        double exposed = route.getExposureMeters(hazardClass);
        if (exposed <= 0) return;
        String street = streetWhereHazardStarts(route, hazardClass);
        String title = hazardClass.getLabel() + " flood hazard on " + (street.isEmpty() ? "this route" : street);
        String detail = meters(exposed) + " in NOAH " + hazardClass.getLabel().toLowerCase(Locale.ROOT)
                + " hazard (" + hazardClass.getDepthRange() + " deep)";
        warnings.add(new RouteWarning(kind, title, detail));
    }

    /** The street of the last turn before the first sample with this hazard class, if known. */
    private static String streetWhereHazardStarts(RouteAssessment route, HazardClass hazardClass) {
        double startsAt = -1;
        for (RouteSample sample : route.getSamples()) {
            if (sample.getVerdict().getEffectiveHazard() == hazardClass) {
                startsAt = sample.getDistanceAlongMeters();
                break;
            }
        }
        if (startsAt < 0) return "";
        String street = "";
        for (Maneuver maneuver : route.getCandidate().getManeuvers()) {
            if (maneuver.getRouteOffsetMeters() <= startsAt && !maneuver.getStreet().isEmpty()) {
                street = maneuver.getStreet();
            }
        }
        return street;
    }

    private static String meters(double value) {
        return Math.round(value) + " m";
    }
}
