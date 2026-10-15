package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.TravelMode;

import java.util.List;

/**
 * A RouteProvider that can plan detours around given areas, e.g. a bridge the LGU closed.
 * TomTom supports this ("avoidAreas"); OpenStreetMap's public OSRM server does not.
 * Interface inheritance: it extends RouteProvider and adds one capability.
 */
public interface AvoidingRouteProvider extends RouteProvider {

    RouteResponse requestRoutesAvoiding(GeoPoint origin, GeoPoint destination, TravelMode mode, int maxAlternatives,
                                        List<AvoidArea> avoidAreas) throws OfflineException, ProviderException;
}
