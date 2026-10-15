package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteWarning;
import com.rutarescue.model.route.TravelMode;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Phase 1 warning drawer: what a resident should know before starting a route. */
public class RouteWarningBuilderTest {

    private final RouteWarningBuilder builder = new RouteWarningBuilder();

    @Test
    public void aClearRouteHasNoWarnings() {
        RouteAssessment clear = RouteTestData.route(0, 1000, 600).build();
        assertTrue(builder.build(clear, TravelMode.WALKING, true).isEmpty());
    }

    @Test
    public void highHazardNamesTheStreetAndTheMetres() {
        RouteAssessment route = RouteTestData.route(0, 2600, 1680)
                .turn("Head west", "Quezon Blvd", 0)
                .turn("Turn left", "Recto Ave", 700)
                .hazard(HazardClass.HIGH, 800, 1150).build();
        List<RouteWarning> warnings = builder.build(route, TravelMode.WALKING, true);
        RouteWarning high = warnings.get(0);
        assertEquals(RouteWarning.Kind.HIGH_FLOOD, high.getKind());
        assertEquals("High flood hazard on Recto Ave", high.getTitle());
        assertEquals("350 m in NOAH high hazard (over 1.5 m deep)", high.getDetail());
        assertTrue(high.isSerious());
    }

    @Test
    public void mediumHazardIsACautionNotSerious() {
        RouteAssessment route = RouteTestData.route(0, 3000, 1980).hazard(HazardClass.MEDIUM, 500, 620).build();
        RouteWarning medium = builder.build(route, TravelMode.WALKING, true).get(0);
        assertEquals("Medium flood hazard on this route", medium.getTitle());
        assertEquals("120 m in NOAH medium hazard (0.5-1.5 m deep)", medium.getDetail());
        assertFalse(medium.isSerious());
    }

    @Test
    public void unverifiedBridgesAreListed() {
        RouteAssessment route = RouteTestData.route(0, 1000, 600).unverifiedStructure(300, 360).build();
        RouteWarning bridge = builder.build(route, TravelMode.WALKING, true).get(0);
        assertEquals(RouteWarning.Kind.UNVERIFIED_STRUCTURE, bridge.getKind());
        assertEquals("Unverified bridge or tunnel", bridge.getTitle());
        assertEquals("60 m on structures the LGU has not confirmed", bridge.getDetail());
    }

    @Test
    public void aClosureIsSeriousAndComesFirst() {
        RouteAssessment route = RouteTestData.route(0, 1000, 600).hazard(HazardClass.MEDIUM, 0, 100).closed().build();
        List<RouteWarning> warnings = builder.build(route, TravelMode.WALKING, true);
        assertEquals(RouteWarning.Kind.CLOSURE, warnings.get(0).getKind());
        assertTrue(warnings.get(0).isSerious());
    }

    @Test
    public void trafficDelayIsSlowOrHeavy() {
        RouteAssessment slow = RouteTestData.route(0, 3000, 1500).traffic(4 * 60).build();
        RouteAssessment heavy = RouteTestData.route(0, 3000, 1500).traffic(9 * 60).build();
        assertEquals("Slow traffic on this route", builder.build(slow, TravelMode.VEHICLE, true).get(0).getTitle());
        RouteWarning heavyWarning = builder.build(heavy, TravelMode.VEHICLE, true).get(0);
        assertEquals("Heavy traffic on this route", heavyWarning.getTitle());
        assertEquals("About 9 minutes of delay · live", heavyWarning.getDetail());
        assertEquals("About 9 minutes of delay · cached", builder.build(heavy, TravelMode.VEHICLE, false).get(0).getDetail());
    }

    @Test
    public void smallDelaysAndWalkingTripsGetNoTrafficWarning() {
        assertTrue(builder.build(RouteTestData.route(0, 3000, 1500).traffic(60).build(), TravelMode.VEHICLE, true).isEmpty());
        assertTrue(builder.build(RouteTestData.route(0, 3000, 1500).traffic(600).build(), TravelMode.WALKING, true).isEmpty());
    }

    @Test
    public void seriousWarningsComeBeforeCautions() {
        RouteAssessment route = RouteTestData.route(0, 2600, 1680).traffic(9 * 60)
                .unverifiedStructure(100, 140).hazard(HazardClass.HIGH, 800, 1150).build();
        List<RouteWarning> warnings = builder.build(route, TravelMode.VEHICLE, true);
        assertEquals(RouteWarning.Kind.HIGH_FLOOD, warnings.get(0).getKind());
        assertEquals(3, warnings.size());
        assertTrue(RouteWarningBuilder.hasSerious(warnings));
    }
}
