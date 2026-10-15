package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureMatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java risk engine (DOCX sections 6 and 8). No Android, Firebase or network code is
 * used here, so it can be unit-tested on any JVM.
 *
 * For each sample of a route it:
 *   1. checks explicit closure/status evidence,
 *   2. classifies the road structure (surface, bridge, tunnel, unknown),
 *   3. only then applies the selected NOAH hazard class,
 * and finally adds up the exposure by distance.
 */
public final class RiskEngine {

    private static final int MAX_NOTES = 6;

    private final List<RiskRule> rules;

    public RiskEngine() {
        this(defaultRules());
    }

    public RiskEngine(List<RiskRule> rules) {
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("At least one rule is required.");
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    /** The order matters: closures first, the plain surface rule last. */
    public static List<RiskRule> defaultRules() {
        return Arrays.asList(new ClosureRule(), new TunnelRule(), new BridgeRule(),
                new UnknownStructureRule(), new SurfaceRule());
    }

    public RouteAssessment assess(RouteCandidate candidate, RiskContext context) {
        RouteSampler sampler = new RouteSampler(context.getStepMeters());
        List<RouteSample> samples = new ArrayList<>();
        EnumMap<HazardClass, Double> exposure = new EnumMap<>(HazardClass.class);
        for (HazardClass hazardClass : HazardClass.values()) exposure.put(hazardClass, 0.0);

        boolean closed = false;
        HazardClass maxHazard = HazardClass.NONE;
        RiskLevel maxLevel = RiskLevel.GREEN;
        double unverified = 0;
        double outOfCoverage = 0;
        double bridge = 0;
        double tunnel = 0;
        Set<String> notes = new LinkedHashSet<>();

        for (RouteSampler.SamplePoint sample : sampler.sample(candidate.getPoints())) {
            SampleFacts facts = collectFacts(candidate, sample, context);
            SampleVerdict verdict = evaluate(facts);
            double length = sample.getLengthMeters();
            samples.add(new RouteSample(sample.getPoint(), sample.getDistanceAlongMeters(), length, verdict));

            closed |= verdict.isClosed();
            maxLevel = RiskLevel.max(maxLevel, verdict.getLevel());
            maxHazard = HazardClass.max(maxHazard, verdict.getEffectiveHazard());
            if (verdict.getEffectiveHazard() != HazardClass.NONE) {
                exposure.put(verdict.getEffectiveHazard(), exposure.get(verdict.getEffectiveHazard()) + length);
            }
            if (verdict.isUnverifiedStructure()) unverified += length;
            if (verdict.isOutOfCoverage()) outOfCoverage += length;
            if (verdict.getStructure() == RoadStructure.BRIDGE) bridge += length;
            if (verdict.getStructure() == RoadStructure.TUNNEL) tunnel += length;
            if (verdict.getLevel() != RiskLevel.GREEN && notes.size() < MAX_NOTES) {
                notes.add(verdict.getReason());
            }
        }
        return new RouteAssessment(candidate, context.getScenario(), samples, closed, maxHazard, maxLevel,
                exposure, unverified, outOfCoverage, bridge, tunnel, new ArrayList<>(notes));
    }

    /** Asks each rule in order; the first rule that applies decides. */
    SampleVerdict evaluate(SampleFacts facts) {
        for (RiskRule rule : rules) {
            if (rule.appliesTo(facts)) {
                return rule.evaluate(facts);
            }
        }
        throw new IllegalStateException("No risk rule applied - SurfaceRule must be last in the list.");
    }

    private SampleFacts collectFacts(RouteCandidate candidate, RouteSampler.SamplePoint sample, RiskContext context) {
        GeoPoint point = sample.getPoint();
        double lat = point.getLatitude();
        double lon = point.getLongitude();
        boolean covered = context.getHazard().covers(lat, lon);
        HazardClass ground = covered ? context.getHazard().classify(lat, lon) : HazardClass.NONE;
        StructureMatch match = context.getStructures().match(point, sample.getBearingDegrees());
        RoadStructureRecord record = match.getRecord();
        boolean verifiedOpen = record != null && match.getType() == record.getType()
                && record.isVerifiedOpen(context.getNow(), context.getVerifiedOpenMaxAgeMs());
        return new SampleFacts(point, ground, covered, match,
                candidate.isTunnelAt(sample.getVertexIndex()),
                context.getClosures().findClosure(point),
                candidate.isProviderClosureAt(sample.getVertexIndex()),
                verifiedOpen);
    }
}
