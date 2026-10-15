package com.rutarescue.persistence;

/**
 * One cached routing answer. We keep the provider's raw JSON (not our assessment), so a
 * cached route can be re-opened offline and re-assessed with ANY flood scenario.
 * Plain fields keep the Gson file format simple.
 */
public final class CachedRouteEntry {

    public String id;
    public String destinationName;
    public double originLat;
    public double originLon;
    public double destinationLat;
    public double destinationLon;
    public String travelMode;
    public long fetchedAt;
    public boolean trafficConsidered;
    public String rawProviderJson;
    /** "TOMTOM" or "OSRM"; older cache files have no value and are TomTom answers. */
    public String provider;

    public CachedRouteEntry() {
        // required by Gson
    }
}
