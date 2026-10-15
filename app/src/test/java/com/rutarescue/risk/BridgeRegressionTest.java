package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DOCX acceptance journey 4 - bridge regression:
 * "the same high-hazard ground crossing is red as a surface road, amber as an unverified
 *  bridge, and red when the bridge is explicitly closed."
 */
public class BridgeRegressionTest {

    private static final long NOW = 1_800_000_000_000L;

    private final RiskEngine engine = new RiskEngine();

    private RouteAssessment assessWith(StructureLookup structures) {
        RiskContext context = new RiskContext(RiskFixtures.riverGrid(), structures, ClosureLookup.NONE, NOW);
        return engine.assess(RiskFixtures.eastWestRoute(0, 600), context);
    }

    @Test
    public void surfaceRoadOverHighHazardIsRed() {
        RouteAssessment result = assessWith(StructureLookup.NONE);
        assertEquals(RiskLevel.RED, result.getMaxLevel());
        assertEquals(HazardClass.HIGH, result.getMaxEffectiveHazard());
        assertEquals(100, result.getExposureMeters(HazardClass.HIGH), 15);
        assertFalse(result.isClosed());
    }

    @Test
    public void unverifiedBridgeDoesNotInheritGroundDepthButIsAmber() {
        RouteAssessment result = assessWith(RiskFixtures.index(RiskFixtures.bridgeOverRiver()));
        assertEquals(RiskLevel.AMBER, result.getMaxLevel());
        assertEquals("the bridge must not inherit HIGH", HazardClass.NONE, result.getMaxEffectiveHazard());
        assertEquals(0, result.getTotalExposureMeters(), 0.01);
        assertTrue(result.getUnverifiedStructureMeters() > 80);
        assertTrue(result.getNotes().get(0).startsWith(BridgeRule.UNVERIFIED_MESSAGE));

        // The hazard below is still recorded for display.
        boolean highBelowShown = false;
        for (RouteSample sample : result.getSamples()) {
            if (sample.getVerdict().getGroundHazard() == HazardClass.HIGH) highBelowShown = true;
        }
        assertTrue(highBelowShown);
    }

    @Test
    public void closedBridgeIsUnavailable() {
        RoadStructureRecord bridge = RiskFixtures.bridgeOverRiver();
        bridge.applyStatus(StructureStatus.CLOSED, "Field report: approach washed out", NOW - 1000, "lgu-uid");
        RouteAssessment result = assessWith(RiskFixtures.index(bridge));
        assertTrue(result.isClosed());
        assertEquals(RiskLevel.UNAVAILABLE, result.getMaxLevel());
    }

    @Test
    public void freshlyVerifiedOpenBridgeIsGreenButStillMentionsHazardBelow() {
        RoadStructureRecord bridge = RiskFixtures.bridgeOverRiver();
        bridge.applyStatus(StructureStatus.VERIFIED_OPEN, "Inspected this morning", NOW - 60_000, "lgu-uid");
        RouteAssessment result = assessWith(RiskFixtures.index(bridge));
        assertEquals(RiskLevel.GREEN, result.getMaxLevel());
        assertEquals(0, result.getUnverifiedStructureMeters(), 0.01);
        boolean mentioned = false;
        for (RouteSample sample : result.getSamples()) {
            if (sample.getVerdict().getReason().contains("verified open")
                    && sample.getVerdict().getReason().contains("High hazard mapped below")) {
                mentioned = true;
            }
        }
        assertTrue(mentioned);
    }

    @Test
    public void staleVerifiedOpenEvidenceFallsBackToUnverified() {
        RoadStructureRecord bridge = RiskFixtures.bridgeOverRiver();
        bridge.applyStatus(StructureStatus.VERIFIED_OPEN, "Old inspection", NOW - 3L * 24 * 60 * 60 * 1000, "lgu-uid");
        RouteAssessment result = assessWith(RiskFixtures.index(bridge));
        assertEquals(RiskLevel.AMBER, result.getMaxLevel());
    }

    @Test
    public void rankingPrefersUnverifiedBridgeOverSurfaceButNotOverClosedBridge() {
        RouteAssessment surface = assessWith(StructureLookup.NONE);
        RouteAssessment bridge = assessWith(RiskFixtures.index(RiskFixtures.bridgeOverRiver()));
        RouteRanker ranker = new RouteRanker();
        assertTrue(ranker.compare(bridge, surface) < 0);

        RoadStructureRecord closedBridge = RiskFixtures.bridgeOverRiver();
        closedBridge.applyStatus(StructureStatus.CLOSED, "Closed", NOW, "lgu-uid");
        RouteAssessment closed = assessWith(RiskFixtures.index(closedBridge));
        assertTrue(ranker.compare(surface, closed) < 0);
    }
}
