package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.TravelMode;

import java.util.List;
import java.util.Locale;

/**
 * Requests up to three route alternatives from the TomTom Routing API (free tier, no payment:
 * a free developer key covers 20,000 routing requests a month). Live traffic is included for
 * motorcycle and car trips.
 * Ported from the legacy Python get_route(), but with the travel mode selectable and
 * tunnel/traffic sections requested for the structure-aware assessment.
 */
public final class TomTomRouteProvider implements AvoidingRouteProvider {

    private static final String BASE_URL = "https://api.tomtom.com/routing/1/calculateRoute/";

    private final HttpJsonClient http;
    private final String apiKey;

    public TomTomRouteProvider(HttpJsonClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws OfflineException, ProviderException {
        String json = http.get(url(origin, destination, mode, maxAlternatives));
        return response(json, mode);
    }

    /** Same request, sent as POST with rectangles TomTom must route around (e.g. a closed bridge). */
    @Override
    public RouteResponse requestRoutesAvoiding(GeoPoint origin, GeoPoint destination, TravelMode mode,
                                               int maxAlternatives, List<AvoidArea> avoidAreas)
            throws OfflineException, ProviderException {
        String json = http.post(url(origin, destination, mode, maxAlternatives), avoidAreasJson(avoidAreas));
        return response(json, mode);
    }

    private String url(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws ProviderException {
        if (apiKey.isEmpty()) {
            throw new ProviderException("No TomTom API key is configured (TOMTOM_API_KEY in local.properties).", -1);
        }
        return BASE_URL
                + coordinate(origin) + ":" + coordinate(destination) + "/json"
                + "?key=" + apiKey
                + "&travelMode=" + mode.getTomTomValue()
                + "&routeType=fastest"
                + "&maxAlternatives=" + Math.max(0, Math.min(2, maxAlternatives))
                + "&alternativeType=anyRoute"
                + "&traffic=" + mode.usesTraffic()
                + "&instructionsType=text"
                + "&language=en-GB"
                + "&sectionType=tunnel&sectionType=traffic";
    }

    private RouteResponse response(String json, TravelMode mode) throws ProviderException {
        return new RouteResponse(TomTomRouteParser.parse(json, mode.usesTraffic()), json,
                System.currentTimeMillis(), true, null, RouteResponse.PROVIDER_TOMTOM);
    }

    /** {"avoidAreas":{"rectangles":[{"southWestCorner":{...},"northEastCorner":{...}}]}} */
    static String avoidAreasJson(List<AvoidArea> areas) {
        StringBuilder json = new StringBuilder("{\"avoidAreas\":{\"rectangles\":[");
        for (int i = 0; i < areas.size(); i++) {
            AvoidArea area = areas.get(i);
            if (i > 0) json.append(',');
            json.append(String.format(Locale.ROOT,
                    "{\"southWestCorner\":{\"latitude\":%.6f,\"longitude\":%.6f},"
                            + "\"northEastCorner\":{\"latitude\":%.6f,\"longitude\":%.6f}}",
                    area.getSouthWest().getLatitude(), area.getSouthWest().getLongitude(),
                    area.getNorthEast().getLatitude(), area.getNorthEast().getLongitude()));
        }
        return json.append("]}}").toString();
    }

    @Override
    public boolean isLive() {
        return true;
    }

    private static String coordinate(GeoPoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.getLatitude(), point.getLongitude());
    }
}
