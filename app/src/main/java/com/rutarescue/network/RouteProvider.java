package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.TravelMode;

/**
 * Source of route alternatives.
 *
 * Polymorphism: TomTomRouteProvider asks the online API; CachedRouteProvider re-opens a
 * previously saved answer when offline. RouteService does not care which one it uses.
 */
public interface RouteProvider {

    /** Up to (1 + maxAlternatives) routes from origin to destination. */
    RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws OfflineException, ProviderException;

    /** Whether this provider returns live data (affects the "live"/"cached" label). */
    boolean isLive();
}
