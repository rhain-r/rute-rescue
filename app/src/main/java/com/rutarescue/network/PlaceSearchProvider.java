package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.place.PlaceResult;

import java.util.List;

/**
 * Destination search (needs internet).
 *
 * Polymorphism: NominatimSearchProvider (OpenStreetMap, free, no key) and TomTomSearchProvider
 * both implement it; RouteService does not care which one it uses.
 */
public interface PlaceSearchProvider {

    List<PlaceResult> search(String query, GeoPoint near) throws OfflineException, ProviderException;

    /**
     * Whether the provider may be called on every keystroke. OpenStreetMap's Nominatim forbids
     * search-as-you-type, so it is only called when the user presses Search.
     */
    boolean allowsSearchAsYouType();
}
