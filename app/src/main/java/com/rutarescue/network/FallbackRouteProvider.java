package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.TravelMode;
import com.rutarescue.util.SafeLog;

import java.util.List;

/**
 * Asks the preferred provider first (TomTom: live traffic) and, if it cannot answer (no key,
 * rejected key, free monthly quota used up, server error), asks the backup provider
 * (OpenStreetMap / OSRM: free, no traffic). A lost connection is NOT retried with the backup,
 * because the backup would fail the same way; RouteService then uses the offline cache.
 *
 * Polymorphism: this class is itself a RouteProvider that wraps two other RouteProviders.
 */
public final class FallbackRouteProvider implements AvoidingRouteProvider {

    private static final String TAG = "RR-Route";

    private final RouteProvider preferred;
    private final RouteProvider backup;

    public FallbackRouteProvider(RouteProvider preferred, RouteProvider backup) {
        if (preferred == null || backup == null) throw new IllegalArgumentException("Both providers are required.");
        this.preferred = preferred;
        this.backup = backup;
    }

    @Override
    public RouteResponse requestRoutes(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives)
            throws OfflineException, ProviderException {
        try {
            return preferred.requestRoutes(origin, destination, mode, maxAlternatives);
        } catch (ProviderException preferredFailed) {
            SafeLog.w(TAG, "Preferred routing provider failed; using OpenStreetMap routing", preferredFailed);
            return backup.requestRoutes(origin, destination, mode, maxAlternatives);
        }
    }

    /**
     * Detours around closures: only the preferred provider (TomTom) can avoid areas, so this
     * fails with a ProviderException when it cannot, and the caller keeps the routes it has.
     */
    @Override
    public RouteResponse requestRoutesAvoiding(GeoPoint origin, GeoPoint destination, TravelMode mode,
                                               int maxAlternatives, List<AvoidArea> avoidAreas)
            throws OfflineException, ProviderException {
        if (!(preferred instanceof AvoidingRouteProvider)) {
            throw new ProviderException("This routing provider cannot plan detours around closures.", -1);
        }
        return ((AvoidingRouteProvider) preferred).requestRoutesAvoiding(origin, destination, mode, maxAlternatives,
                avoidAreas);
    }

    @Override
    public boolean isLive() {
        return true;
    }
}
