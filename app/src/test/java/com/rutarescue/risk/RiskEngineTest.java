package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.IndexRange;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RiskEngineTest {

    private static final long NOW = 1_800_000_000_000L;
    private final RiskEngine engine = new RiskEngine();

    private RiskContext context(ClosureLookup closures) {
        return new RiskContext(RiskFixtures.riverGrid(), StructureLookup.NONE, closures, NOW);
    }

    @Test
    public void exposureIsMeasuredByDistanceNotVertexCount() {
        RouteAssessment sparse = engine.assess(RiskFixtures.eastWestRoute(0, 600), context(ClosureLookup.NONE));
        RouteAssessment dense = engine.assess(RiskFixtures.denseEastWestRoute(), context(ClosureLookup.NONE));
        assertEquals(sparse.getTotalExposureMeters(), dense.getTotalExposureMeters(), 10.0);
    }

    @Test
    public void lowAndMediumSurfaceHazardsAreAmberWithTheirOwnExposure() {
        RiskContext medium = new RiskContext(RiskFixtures.gridWithStrip(HazardClass.MEDIUM), StructureLookup.NONE,
                ClosureLookup.NONE, NOW);
        RouteAssessment result = engine.assess(RiskFixtures.eastWestRoute(0, 600), medium);
        assertEquals(RiskLevel.AMBER, result.getMaxLevel());
        assertEquals(HazardClass.MEDIUM, result.getMaxEffectiveHazard());
        assertTrue(result.getExposureMeters(HazardClass.MEDIUM) > 80);
    }

    @Test
    public void floodedTunnelFromProviderSectionIsRedAndTreatedAsHigh() {
        RouteCandidate tunnelRoute = RiskFixtures.route(0, 600, Arrays.asList(
                        new GeoPoint(14.6, 121.0000), new GeoPoint(14.6, 121.0018),
                        new GeoPoint(14.6, 121.0032), new GeoPoint(14.6, 121.0050)),
                Collections.singletonList(new IndexRange(1, 2)));
        RiskContext medium = new RiskContext(RiskFixtures.gridWithStrip(HazardClass.LOW), StructureLookup.NONE,
                ClosureLookup.NONE, NOW);
        RouteAssessment result = engine.assess(tunnelRoute, medium);
        assertEquals(RiskLevel.RED, result.getMaxLevel());
        assertEquals(HazardClass.HIGH, result.getMaxEffectiveHazard());
        assertTrue(result.getTunnelMeters() > 100);
    }

    @Test
    public void lguClosureMakesRouteUnavailable() throws Exception {
        RoadClosure closure = new RoadClosure("c1", "Flooded intersection", new GeoPoint(14.6, 121.004), 30,
                "Knee-deep water reported by barangay", MetroManilaCity.MANILA, true, NOW);
        RouteAssessment result = engine.assess(RiskFixtures.eastWestRoute(0, 600),
                context(ClosureLookup.of(Collections.singletonList(closure))));
        assertTrue(result.isClosed());
        assertEquals(RiskLevel.UNAVAILABLE, result.getMaxLevel());
    }

    @Test
    public void pointsOutsideCoverageAreNeverGreen() {
        RouteCandidate outside = RiskFixtures.route(0, 600, Arrays.asList(
                new GeoPoint(14.7, 121.1), new GeoPoint(14.7, 121.101)), Collections.emptyList());
        RouteAssessment result = engine.assess(outside, context(ClosureLookup.NONE));
        assertEquals(RiskLevel.AMBER, result.getMaxLevel());
        assertTrue(result.getOutOfCoverageMeters() > 100);
    }
}
