package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.TravelMode;
import com.rutarescue.persistence.CachedRouteEntry;
import com.rutarescue.persistence.RouteCacheStore;
import com.rutarescue.util.GeoMath;

/**
 * Offline fallback: re-opens the newest cached answer for the same destination and mode.
 * The result is marked NOT live, so the screen labels its traffic/time as cached.
 */
public final class CachedRouteProvider implements RouteProvider {

    /** A cached trip counts as "the same" if both ends are within 150 m. */
    private static final double SAME_PLACE_METERS = 150;

    private final RouteCacheStore cache;

    public CachedRouteProvider(RouteCacheStore cache) {
        this.cache = cache;
    }

    @Override
    public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws OfflineException, ProviderException {
        for (CachedRouteEntry entry : cache.list()) {
            boolean sameMode = mode.name().equals(entry.travelMode);
            boolean sameDestination = GeoMath.distanceMeters(destination,
                    new GeoPoint(entry.destinationLat, entry.destinationLon)) <= SAME_PLACE_METERS;
            boolean sameOrigin = GeoMath.distanceMeters(origin,
                    new GeoPoint(entry.originLat, entry.originLon)) <= SAME_PLACE_METERS;
            if (sameMode && sameDestination && sameOrigin) {
                return fromEntry(entry);
            }
        }
        throw new OfflineException("You are offline and there is no cached route for this trip. "
                + "New routes need internet.");
    }

    /** Rebuilds a response from a specific cache entry (used by "Cached routes" list). */
    public RouteResponse fromEntry(CachedRouteEntry entry) throws ProviderException {
        // Each provider has its own answer format, so the cache remembers which one was used.
        if (RouteResponse.PROVIDER_OSRM.equals(entry.provider)) {
            return new RouteResponse(OsrmRouteParser.parse(entry.rawProviderJson), entry.rawProviderJson,
                    entry.fetchedAt, false, entry.id, RouteResponse.PROVIDER_OSRM);
        }
        return new RouteResponse(TomTomRouteParser.parse(entry.rawProviderJson, entry.trafficConsidered),
                entry.rawProviderJson, entry.fetchedAt, false, entry.id, RouteResponse.PROVIDER_TOMTOM);
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
