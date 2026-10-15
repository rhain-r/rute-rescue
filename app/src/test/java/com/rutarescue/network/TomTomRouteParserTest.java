package com.rutarescue.network;

import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.route.RouteCandidate;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Parses a hand-written TomTom-style answer (no network needed). */
public class TomTomRouteParserTest {

    private static final String JSON = "{\"routes\":["
            + "{\"summary\":{\"lengthInMeters\":1200,\"travelTimeInSeconds\":600,\"trafficDelayInSeconds\":45},"
            + " \"legs\":[{\"points\":[{\"latitude\":14.6,\"longitude\":121.0},{\"latitude\":14.6,\"longitude\":121.001}]},"
            + "           {\"points\":[{\"latitude\":14.6,\"longitude\":121.001},{\"latitude\":14.601,\"longitude\":121.002}]}],"
            + " \"sections\":[{\"sectionType\":\"TUNNEL\",\"startPointIndex\":1,\"endPointIndex\":2},"
            + "               {\"sectionType\":\"TRAFFIC\",\"simpleCategory\":\"ROAD_CLOSURE\",\"startPointIndex\":0,\"endPointIndex\":1},"
            + "               {\"sectionType\":\"TRAFFIC\",\"simpleCategory\":\"JAM\",\"startPointIndex\":1,\"endPointIndex\":2}],"
            + " \"guidance\":{\"instructions\":[{\"message\":\"Leave from Mabini Street\",\"street\":\"Mabini Street\","
            + "   \"point\":{\"latitude\":14.6,\"longitude\":121.0},\"routeOffsetInMeters\":0},"
            + "  {\"message\":\"Turn left onto Rizal Avenue\",\"street\":\"Rizal Avenue\","
            + "   \"point\":{\"latitude\":14.6,\"longitude\":121.001},\"routeOffsetInMeters\":110}]}},"
            + "{\"summary\":{\"lengthInMeters\":1500,\"travelTimeInSeconds\":700},"
            + " \"legs\":[{\"points\":[{\"latitude\":14.6,\"longitude\":121.0},{\"latitude\":14.602,\"longitude\":121.002}]}]}"
            + "]}";

    @Test
    public void parsesRoutesSectionsAndInstructions() throws Exception {
        List<RouteCandidate> routes = TomTomRouteParser.parse(JSON, true);
        assertEquals(2, routes.size());

        RouteCandidate first = routes.get(0);
        assertEquals(3, first.getPoints().size()); // the duplicated leg joint is removed
        assertEquals(600, first.getTravelTimeSeconds());
        assertEquals(45, first.getTrafficDelaySeconds());
        assertTrue(first.isTunnelAt(1));
        assertFalse(first.isTunnelAt(0));
        assertTrue(first.isProviderClosureAt(0));
        assertFalse("a traffic jam is not a closure", first.isProviderClosureAt(1));
        assertEquals("via Mabini Street and Rizal Avenue", first.getViaDescription());
        assertEquals("Route A", first.getLetterLabel());
        assertEquals("Route B", routes.get(1).getLetterLabel());
    }

    @Test
    public void emptyAnswerIsAProviderError() {
        assertThrows(ProviderException.class, () -> TomTomRouteParser.parse("{\"routes\":[]}", false));
        assertThrows(ProviderException.class, () -> TomTomRouteParser.parse("not json", false));
    }
}
