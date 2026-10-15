package com.rutarescue.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.route.TravelMode;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The free OpenStreetMap providers: OSRM routing and Nominatim search. The JSON fixtures are
 * real answers saved from routing.openstreetmap.de and nominatim.openstreetmap.org
 * (© OpenStreetMap contributors).
 */
public class OpenStreetMapProvidersTest {

    @Test
    public void osrmRouteAcrossMacArthurBridgeIsParsed() throws Exception {
        List<RouteCandidate> routes = OsrmRouteParser.parse(fixture("osrm_macarthur_foot.json"));
        RouteCandidate route = routes.get(0);
        assertTrue("about 466 m on foot", route.getLengthMeters() > 400 && route.getLengthMeters() < 550);
        assertTrue(route.getTravelTimeSeconds() > 0);
        assertTrue(route.getPoints().size() > 5);
        // OSRM geometry is [longitude, latitude]: the first point must be in Manila, not swapped.
        GeoPoint start = route.getPoints().get(0);
        assertEquals(14.59, start.getLatitude(), 0.02);
        assertEquals(120.98, start.getLongitude(), 0.02);
        // Typical speeds only: never presented as live traffic.
        assertFalse(route.isTrafficConsidered());
        assertEquals(0, route.getTrafficDelaySeconds());
        assertTrue(route.getViaDescription().contains("MacArthur Bridge"));
        assertEquals("Arrive at your destination", route.getManeuvers().get(route.getManeuvers().size() - 1).getMessage());
    }

    @Test
    public void osrmNoRouteBecomesAFriendlyProviderError() {
        ProviderException error = assertThrows(ProviderException.class,
                () -> OsrmRouteParser.parse("{\"code\":\"NoRoute\",\"message\":\"Impossible route\"}"));
        assertEquals("No route was found between these points.", error.getMessage());
    }

    @Test
    public void malformedOsrmAnswerIsRejected() {
        assertThrows(ProviderException.class, () -> OsrmRouteParser.parse("not json"));
    }

    @Test
    public void nominatimResultsAreParsedInsideMetroManila() throws Exception {
        List<PlaceResult> places = NominatimSearchProvider.parse(fixture("nominatim_manila_city_hall.json"));
        assertFalse(places.isEmpty());
        PlaceResult first = places.get(0);
        assertEquals("Manila City Hall", first.getDisplayName());
        assertEquals(14.59, first.getLocation().getLatitude(), 0.02);
        assertEquals(120.98, first.getLocation().getLongitude(), 0.02);
    }

    @Test
    public void nominatimIsNeverCalledOnEveryKeystroke() {
        assertFalse(new NominatimSearchProvider(new HttpJsonClient(1000)).allowsSearchAsYouType());
    }

    @Test
    public void fallbackUsesOpenStreetMapWhenTomTomCannotAnswer() throws Exception {
        RouteProvider failing = new RouteProvider() {
            @Override
            public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int max)
                    throws ProviderException {
                throw new ProviderException("Free monthly quota used up (HTTP 429).", 429);
            }

            @Override
            public boolean isLive() {
                return true;
            }
        };
        String osrmJson = fixture("osrm_macarthur_foot.json");
        RouteProvider osm = new RouteProvider() {
            @Override
            public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int max)
                    throws ProviderException {
                return new RouteResponse(OsrmRouteParser.parse(osrmJson), osrmJson, 1L, true, null,
                        RouteResponse.PROVIDER_OSRM);
            }

            @Override
            public boolean isLive() {
                return true;
            }
        };
        RouteResponse response = new FallbackRouteProvider(failing, osm).requestRoutes(
                new GeoPoint(14.5948, 120.9801), new GeoPoint(14.5990, 120.9808), TravelMode.WALKING, 2);
        assertEquals(RouteResponse.PROVIDER_OSRM, response.getProvider());
        assertFalse("OSRM answers never claim live traffic", response.hasLiveTraffic());
        assertNotNull(response.getRawJson());
    }

    @Test
    public void tomTomAvoidAreasBodyFollowsTheApiFormat() {
        String json = TomTomRouteProvider.avoidAreasJson(java.util.Collections.singletonList(
                new com.rutarescue.model.route.AvoidArea(new GeoPoint(14.5948, 120.9765), new GeoPoint(14.5968, 120.9785))));
        assertEquals("{\"avoidAreas\":{\"rectangles\":[{\"southWestCorner\":{\"latitude\":14.594800,\"longitude\":120.976500},"
                + "\"northEastCorner\":{\"latitude\":14.596800,\"longitude\":120.978500}}]}}", json);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = OpenStreetMapProvidersTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("Missing test fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
