package com.rutarescue.risk;

import com.rutarescue.model.route.RouteAssessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a ranked list into plain sentences that explain WHY the first route ranks above
 * each alternative (DOCX section 8: "The explanation object produced by RiskEngine is
 * displayed to the user and is unit-testable").
 */
public final class RouteExplainer {

    public List<String> explain(List<RouteAssessment> ranked, boolean trafficIsLive) {
        List<String> lines = new ArrayList<>();
        if (ranked == null || ranked.isEmpty()) {
            lines.add("No route was returned for this trip.");
            return lines;
        }
        RouteAssessment top = ranked.get(0);
        lines.add(summary(top, trafficIsLive));
        if (ranked.size() == 1) {
            lines.add("Only one route was available, so it could not be compared. Review the risks above before travelling.");
            return lines;
        }
        for (int i = 1; i < ranked.size(); i++) {
            lines.add(compare(top, ranked.get(i), trafficIsLive));
        }
        if (top.isClosed()) {
            lines.add("Every available route crosses a reported closure. Consider waiting for LGU updates or choosing another destination.");
        }
        return lines;
    }

    /** One-sentence summary of a route's measures. */
    public String summary(RouteAssessment route, boolean trafficIsLive) {
        StringBuilder out = new StringBuilder();
        out.append(name(route)).append(": highest applicable hazard ")
                .append(route.getMaxEffectiveHazard().getLabel().toLowerCase(Locale.ROOT))
                .append("; ").append(meters(route.getTotalExposureMeters())).append(" exposed to mapped flood hazard; ")
                .append(meters(route.getUnverifiedStructureMeters())).append(" on unverified bridges/structures; ")
                .append(minutes(route.getCandidate().getTravelTimeSeconds()))
                .append(trafficIsLive ? " (live traffic)." : " (provider estimate, not live).");
        if (route.isClosed()) {
            out.append(" It crosses a reported closure.");
        }
        if (route.getOutOfCoverageMeters() > 0) {
            out.append(" ").append(meters(route.getOutOfCoverageMeters()))
                    .append(" lie outside the prepared NOAH coverage.");
        }
        return out.toString();
    }

    /** Explains the first criterion on which the top route beats the other one. */
    String compare(RouteAssessment top, RouteAssessment other, boolean trafficIsLive) {
        String otherName = name(other);
        if (top.isClosed() != other.isClosed()) {
            return otherName + " is ranked lower because it crosses a confirmed closure or impassable structure.";
        }
        if (top.getMaxEffectiveHazard() != other.getMaxEffectiveHazard()) {
            return otherName + " reaches " + other.getMaxEffectiveHazard().getLabel().toLowerCase(Locale.ROOT)
                    + " hazard, while the top route's highest is "
                    + top.getMaxEffectiveHazard().getLabel().toLowerCase(Locale.ROOT) + ".";
        }
        long exposureDifference = Math.round(other.getTotalExposureMeters()) - Math.round(top.getTotalExposureMeters());
        if (exposureDifference != 0) {
            return "The top route has " + meters(exposureDifference) + " less exposure to mapped flood hazard than "
                    + otherName + " (" + meters(top.getTotalExposureMeters()) + " vs "
                    + meters(other.getTotalExposureMeters()) + ").";
        }
        long structureDifference = Math.round(other.getUnverifiedStructureMeters()) - Math.round(top.getUnverifiedStructureMeters());
        if (structureDifference != 0) {
            return "The top route has " + meters(structureDifference) + " less distance on unverified bridges/structures than "
                    + otherName + ".";
        }
        long timeDifference = other.getCandidate().getTravelTimeSeconds() - top.getCandidate().getTravelTimeSeconds();
        if (timeDifference != 0) {
            return "Flood measures are equal; the top route is faster than " + otherName + " by "
                    + minutes(timeDifference) + (trafficIsLive ? " (live traffic)." : " (provider estimate).");
        }
        return otherName + " is equivalent on every measure.";
    }

    private static String name(RouteAssessment route) {
        String via = route.getCandidate().getViaDescription();
        return route.getCandidate().getLetterLabel() + (via.isEmpty() ? "" : " (" + via + ")");
    }

    static String meters(double meters) {
        long rounded = Math.round(meters);
        if (rounded >= 1000) {
            return String.format(Locale.ROOT, "%.1f km", rounded / 1000.0);
        }
        return rounded + " m";
    }

    static String minutes(long seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        return minutes + " min";
    }
}
