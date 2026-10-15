package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.route.SampleVerdict;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Small builder for route assessments in tests: a straight route of 10 m samples where
 * chosen stretches carry a hazard class, an unverified structure or a closure.
 */
final class RouteTestData {

    private final int index;
    private final long seconds;
    private final double lengthMeters;
    private long trafficDelaySeconds;
    private boolean trafficConsidered;
    private final List<Maneuver> maneuvers = new ArrayList<>();
    private final HazardClass[] hazards;
    private final boolean[] unverified;
    private boolean closed;

    private RouteTestData(int index, double lengthMeters, long seconds) {
        this.index = index;
        this.lengthMeters = lengthMeters;
        this.seconds = seconds;
        int count = (int) Math.round(lengthMeters / 10);
        hazards = new HazardClass[count];
        Arrays.fill(hazards, HazardClass.NONE);
        unverified = new boolean[count];
    }

    static RouteTestData route(int index, double lengthMeters, long seconds) {
        return new RouteTestData(index, lengthMeters, seconds);
    }

    /** Marks metres [from, to) of the route with a hazard class. */
    RouteTestData hazard(HazardClass hazardClass, double fromMeters, double toMeters) {
        for (int i = (int) (fromMeters / 10); i < (int) (toMeters / 10) && i < hazards.length; i++) hazards[i] = hazardClass;
        return this;
    }

    RouteTestData unverifiedStructure(double fromMeters, double toMeters) {
        for (int i = (int) (fromMeters / 10); i < (int) (toMeters / 10) && i < unverified.length; i++) unverified[i] = true;
        return this;
    }

    RouteTestData closed() {
        closed = true;
        return this;
    }

    RouteTestData traffic(long delaySeconds) {
        trafficConsidered = true;
        trafficDelaySeconds = delaySeconds;
        return this;
    }

    RouteTestData turn(String message, String street, double atMeters) {
        maneuvers.add(new Maneuver(message, street, new GeoPoint(14.6, 121.0), atMeters));
        return this;
    }

    RouteAssessment build() {
        List<GeoPoint> points = Arrays.asList(new GeoPoint(14.6, 121.0), new GeoPoint(14.6, 121.0 + lengthMeters / 108_000.0));
        RouteCandidate candidate = new RouteCandidate(index, points, lengthMeters, seconds, trafficDelaySeconds,
                trafficConsidered, Collections.emptyList(), Collections.emptyList(), maneuvers);
        List<RouteSample> samples = new ArrayList<>();
        EnumMap<HazardClass, Double> exposure = new EnumMap<>(HazardClass.class);
        HazardClass max = HazardClass.NONE;
        RiskLevel maxLevel = RiskLevel.GREEN;
        double unverifiedMeters = 0;
        for (int i = 0; i < hazards.length; i++) {
            RiskLevel level = hazards[i] == HazardClass.HIGH ? RiskLevel.RED
                    : hazards[i] == HazardClass.NONE && !unverified[i] ? RiskLevel.GREEN : RiskLevel.AMBER;
            SampleVerdict verdict = SampleVerdict.builder("Test", level, "test")
                    .ground(hazards[i]).effective(hazards[i]).unverifiedStructure(unverified[i]).build();
            samples.add(new RouteSample(new GeoPoint(14.6, 121.0 + i * 10 / 108_000.0), i * 10.0, 10, verdict));
            if (hazards[i] != HazardClass.NONE) exposure.merge(hazards[i], 10.0, Double::sum);
            if (unverified[i]) unverifiedMeters += 10;
            max = HazardClass.max(max, hazards[i]);
            maxLevel = RiskLevel.max(maxLevel, level);
        }
        return new RouteAssessment(candidate, FloodScenario.FIVE_YEAR, samples, closed, max,
                closed ? RiskLevel.UNAVAILABLE : maxLevel, exposure, unverifiedMeters, 0, 0, 0, Collections.emptyList());
    }
}
