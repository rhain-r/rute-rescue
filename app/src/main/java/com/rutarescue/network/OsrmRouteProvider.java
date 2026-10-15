package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.route.TravelMode;

import java.util.List;
import java.util.Locale;

/**
 * Route alternatives from OpenStreetMap routing (OSRM on the FOSSGIS community server
 * routing.openstreetmap.de). Free, no key and no payment. It is used when no TomTom key is
 * configured, or as the fallback when TomTom cannot answer.
 *
 * Profiles: walking uses "routed-foot"; motorcycle and car use "routed-car" (there is no
 * motorcycle profile). Times are typical speeds, so the screen labels them "not live traffic".
 * Usage policy: light, fair use with attribution (© OpenStreetMap contributors).
 */
public final class OsrmRouteProvider implements RouteProvider {

    private static final String BASE_URL = "https://routing.openstreetmap.de/";

    private final HttpJsonClient http;

    public OsrmRouteProvider(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws OfflineException, ProviderException {
        String profile = mode == TravelMode.WALKING ? "routed-foot" : "routed-car";
        // OSRM coordinates are "longitude,latitude"; "alternatives" is the maximum number of extra routes.
        String url = BASE_URL + profile + "/route/v1/driving/"
                + coordinate(origin) + ";" + coordinate(destination)
                + "?alternatives=" + Math.max(0, Math.min(2, maxAlternatives))
                + "&overview=full&geometries=geojson&steps=true";
        String json = http.get(url);
        List<RouteCandidate> routes = OsrmRouteParser.parse(json);
        return new RouteResponse(routes, json, System.currentTimeMillis(), true, null, RouteResponse.PROVIDER_OSRM);
    }

    @Override
    public boolean isLive() {
        return true;
    }

    private static String coordinate(GeoPoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.getLongitude(), point.getLatitude());
    }
}
