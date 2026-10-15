package com.rutarescue.network;

import com.rutarescue.model.route.RouteCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Route candidates returned by a RouteProvider, with the raw JSON for caching and the name of
 * the provider that produced it (so a cached answer is re-read with the right parser).
 */
public final class RouteResponse {

    private final List<RouteCandidate> candidates;
    private final String rawJson;
    private final long fetchedAt;
    private final boolean live;
    private final String cacheId;
    private final String provider;

    public static final String PROVIDER_TOMTOM = "TOMTOM";
    public static final String PROVIDER_OSRM = "OSRM";

    public RouteResponse(List<RouteCandidate> candidates, String rawJson, long fetchedAt, boolean live, String cacheId) {
        this(candidates, rawJson, fetchedAt, live, cacheId, PROVIDER_TOMTOM);
    }

    public RouteResponse(List<RouteCandidate> candidates, String rawJson, long fetchedAt, boolean live, String cacheId,
                         String provider) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.rawJson = rawJson;
        this.fetchedAt = fetchedAt;
        this.live = live;
        this.cacheId = cacheId;
        this.provider = provider == null ? PROVIDER_TOMTOM : provider;
    }

    public List<RouteCandidate> getCandidates() { return candidates; }
    public String getRawJson() { return rawJson; }
    public long getFetchedAt() { return fetchedAt; }
    /** True only for a fresh answer from the online provider. */
    public boolean isLive() { return live; }
    public String getCacheId() { return cacheId; }
    /** PROVIDER_TOMTOM or PROVIDER_OSRM. */
    public String getProvider() { return provider; }

    /** True when at least one route's time includes live traffic (TomTom with traffic on). */
    public boolean hasLiveTraffic() {
        if (!live) return false;
        for (RouteCandidate candidate : candidates) {
            if (candidate.isTrafficConsidered()) return true;
        }
        return false;
    }
}
