package com.rutarescue.model.route;

import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The risk engine's evaluation of one route candidate under one flood scenario.
 * All measures are distances in metres so that the result does not depend on how many
 * vertices the provider used (DOCX section 8 "aggregates exposure by distance").
 */
public final class RouteAssessment {

    public static final String TOP_LABEL = "Safer among available alternatives";

    private final RouteCandidate candidate;
    private final FloodScenario scenario;
    private final List<RouteSample> samples;
    private final boolean closed;
    private final HazardClass maxEffectiveHazard;
    private final RiskLevel maxLevel;
    private final EnumMap<HazardClass, Double> exposureMeters;
    private final double unverifiedStructureMeters;
    private final double outOfCoverageMeters;
    private final double bridgeMeters;
    private final double tunnelMeters;
    private final List<String> notes;
    private int rank;
    private int alternativesCount = 1;

    public RouteAssessment(RouteCandidate candidate, FloodScenario scenario, List<RouteSample> samples,
                           boolean closed, HazardClass maxEffectiveHazard, RiskLevel maxLevel,
                           Map<HazardClass, Double> exposureMeters, double unverifiedStructureMeters,
                           double outOfCoverageMeters, double bridgeMeters, double tunnelMeters,
                           List<String> notes) {
        this.candidate = candidate;
        this.scenario = scenario;
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.closed = closed;
        this.maxEffectiveHazard = maxEffectiveHazard;
        this.maxLevel = maxLevel;
        this.exposureMeters = new EnumMap<>(HazardClass.class);
        for (HazardClass hazardClass : HazardClass.values()) {
            Double value = exposureMeters == null ? null : exposureMeters.get(hazardClass);
            this.exposureMeters.put(hazardClass, value == null ? 0.0 : value);
        }
        this.unverifiedStructureMeters = unverifiedStructureMeters;
        this.outOfCoverageMeters = outOfCoverageMeters;
        this.bridgeMeters = bridgeMeters;
        this.tunnelMeters = tunnelMeters;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    /** Total metres exposed to any mapped hazard (LOW + MEDIUM + HIGH) after structure rules. */
    public double getTotalExposureMeters() {
        return exposureMeters.get(HazardClass.LOW) + exposureMeters.get(HazardClass.MEDIUM)
                + exposureMeters.get(HazardClass.HIGH);
    }

    public double getExposureMeters(HazardClass hazardClass) {
        return exposureMeters.get(hazardClass);
    }

    /** Set by RouteRanker after sorting (1 = first). */
    public void setRank(int rank, int alternativesCount) {
        this.rank = rank;
        this.alternativesCount = alternativesCount;
    }

    /**
     * Headline wording required by the DOCX: never "safe route". With only one route
     * there is nothing to compare, so we say so honestly.
     */
    public String getHeadline() {
        // Never call a route "safer" when it crosses a confirmed closure.
        if (closed) return rank == 1 ? "Every available route crosses a reported closure" : "Crosses a reported closure";
        if (alternativesCount <= 1) return "Only route available - review the risks";
        if (rank == 1) return TOP_LABEL;
        return "Alternative " + rank + " of " + alternativesCount;
    }

    public RouteCandidate getCandidate() { return candidate; }
    public FloodScenario getScenario() { return scenario; }
    public List<RouteSample> getSamples() { return samples; }
    public boolean isClosed() { return closed; }
    public HazardClass getMaxEffectiveHazard() { return maxEffectiveHazard; }
    public RiskLevel getMaxLevel() { return maxLevel; }
    public double getUnverifiedStructureMeters() { return unverifiedStructureMeters; }
    public double getOutOfCoverageMeters() { return outOfCoverageMeters; }
    public double getBridgeMeters() { return bridgeMeters; }
    public double getTunnelMeters() { return tunnelMeters; }
    public List<String> getNotes() { return notes; }
    public int getRank() { return rank; }
    public int getAlternativesCount() { return alternativesCount; }
}
