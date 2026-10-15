package com.rutarescue.model.route;

import com.rutarescue.model.common.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One route returned by the routing provider, before RutaRescue assesses its flood risk.
 * Immutable.
 */
public final class RouteCandidate {

    private final int providerIndex;
    private final List<GeoPoint> points;
    private final double lengthMeters;
    private final long travelTimeSeconds;
    private final long trafficDelaySeconds;
    private final boolean trafficConsidered;
    private final List<IndexRange> tunnelSections;
    private final List<IndexRange> closureSections;
    private final List<Maneuver> maneuvers;

    public RouteCandidate(int providerIndex, List<GeoPoint> points, double lengthMeters, long travelTimeSeconds,
                          long trafficDelaySeconds, boolean trafficConsidered, List<IndexRange> tunnelSections,
                          List<IndexRange> closureSections, List<Maneuver> maneuvers) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points.");
        }
        this.providerIndex = providerIndex;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.lengthMeters = lengthMeters;
        this.travelTimeSeconds = travelTimeSeconds;
        this.trafficDelaySeconds = Math.max(0, trafficDelaySeconds);
        this.trafficConsidered = trafficConsidered;
        this.tunnelSections = copy(tunnelSections);
        this.closureSections = copy(closureSections);
        this.maneuvers = copy(maneuvers);
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** Stable letter for the map and the list ("Route A" = the provider's first route). */
    /** A copy with another position in the list (used when detours are added to the routes). */
    public RouteCandidate withProviderIndex(int newIndex) {
        return new RouteCandidate(newIndex, points, lengthMeters, travelTimeSeconds, trafficDelaySeconds,
                trafficConsidered, tunnelSections, closureSections, maneuvers);
    }

    public String getLetterLabel() {
        return "Route " + (char) ('A' + Math.min(providerIndex, 25));
    }

    /** Up to two main street names, e.g. "via Mabini St. and Rizal Ave.". */
    public String getViaDescription() {
        Set<String> streets = new LinkedHashSet<>();
        for (Maneuver maneuver : maneuvers) {
            if (!maneuver.getStreet().isEmpty()) streets.add(maneuver.getStreet());
            if (streets.size() == 2) break;
        }
        if (streets.isEmpty()) return "";
        return "via " + String.join(" and ", streets);
    }

    public boolean isTunnelAt(int vertexIndex) {
        return anyContains(tunnelSections, vertexIndex);
    }

    public boolean isProviderClosureAt(int vertexIndex) {
        return anyContains(closureSections, vertexIndex);
    }

    private static boolean anyContains(List<IndexRange> ranges, int vertexIndex) {
        for (IndexRange range : ranges) {
            if (range.containsSegmentStartingAt(vertexIndex)) return true;
        }
        return false;
    }

    public int getProviderIndex() { return providerIndex; }
    public List<GeoPoint> getPoints() { return points; }
    public double getLengthMeters() { return lengthMeters; }
    public long getTravelTimeSeconds() { return travelTimeSeconds; }
    public long getTrafficDelaySeconds() { return trafficDelaySeconds; }
    public boolean isTrafficConsidered() { return trafficConsidered; }
    public List<IndexRange> getTunnelSections() { return tunnelSections; }
    public List<IndexRange> getClosureSections() { return closureSections; }
    public List<Maneuver> getManeuvers() { return maneuvers; }
}
