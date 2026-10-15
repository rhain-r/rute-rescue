package com.rutarescue.risk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * When every provider route crosses an LGU closure, RouteService asks for detours that avoid
 * the closed stretches. These tests check the areas sent to the provider.
 */
public class ClosureAvoidanceTest {

    private static final long NOW = 1_800_000_000_000L;
    private final RiskEngine engine = new RiskEngine();

    private RouteAssessment assessOverClosedBridge() {
        RoadStructureRecord bridge = RiskFixtures.bridgeOverRiver();
        bridge.applyStatus(StructureStatus.CLOSED, "Inspection (test)", NOW, "lgu-test");
        RiskContext context = new RiskContext(RiskFixtures.riverGrid(), RiskFixtures.index(bridge), ClosureLookup.NONE, NOW);
        return engine.assess(RiskFixtures.eastWestRoute(0, 600), context);
    }

    @Test
    public void allClosedOnlyWhenEveryRouteIsClosed() {
        RouteAssessment closed = assessOverClosedBridge();
        RouteAssessment open = engine.assess(RiskFixtures.eastWestRoute(1, 700),
                new RiskContext(RiskFixtures.riverGrid(), StructureLookup.NONE, ClosureLookup.NONE, NOW));
        assertTrue(ClosureAvoidance.allClosed(Collections.singletonList(closed)));
        assertFalse(ClosureAvoidance.allClosed(Arrays.asList(closed, open)));
        assertFalse("no routes is not 'all closed'", ClosureAvoidance.allClosed(Collections.emptyList()));
    }

    @Test
    public void avoidAreaCoversTheClosedBridgeWithAMargin() {
        RouteAssessment closed = assessOverClosedBridge();
        List<AvoidArea> areas = ClosureAvoidance.areasToAvoid(Collections.singletonList(closed), 40);
        assertEquals("one closed stretch gives one rectangle", 1, areas.size());
        AvoidArea area = areas.get(0);
        // Every closed sample lies inside the rectangle.
        closed.getSamples().stream().filter(s -> s.getVerdict().isClosed()).forEach(s -> {
            GeoPoint p = s.getPoint();
            assertTrue(p.getLatitude() >= area.getSouthWest().getLatitude() && p.getLatitude() <= area.getNorthEast().getLatitude());
            assertTrue(p.getLongitude() >= area.getSouthWest().getLongitude() && p.getLongitude() <= area.getNorthEast().getLongitude());
        });
        // The 40 m margin makes the box clearly taller than the single road line.
        assertTrue(area.getNorthEast().getLatitude() - area.getSouthWest().getLatitude() > 0.0006);
    }

    @Test
    public void overlappingAreasFromSeveralRoutesAreMerged() {
        RouteAssessment first = assessOverClosedBridge();
        RouteAssessment second = assessOverClosedBridge();
        assertEquals(1, ClosureAvoidance.areasToAvoid(Arrays.asList(first, second), 40).size());
    }

    @Test
    public void avoidAreaRejectsSwappedCorners() {
        try {
            new AvoidArea(new GeoPoint(14.60, 121.0), new GeoPoint(14.59, 121.0));
            throw new AssertionError("swapped corners must be rejected");
        } catch (IllegalArgumentException expected) {
            // validated in the constructor (encapsulation)
        }
    }
}
