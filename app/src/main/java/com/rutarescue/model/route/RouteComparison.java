package com.rutarescue.model.route;

import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the Safe Route screen shows: ranked alternatives, the textual explanation of
 * the ranking, and where/when the route data came from.
 */
public final class RouteComparison {

    private final List<RouteAssessment> ranked;
    private final List<String> explanation;
    private final FloodScenario scenario;
    private final TravelMode travelMode;
    private final GeoPoint origin;
    private final GeoPoint destination;
    private final String destinationName;
    private final long fetchedAt;
    private final DataFreshness freshness;
    private final String hazardDatasetVersion;
    private final String cacheId;

    public RouteComparison(List<RouteAssessment> ranked, List<String> explanation, FloodScenario scenario,
                           TravelMode travelMode, GeoPoint origin, GeoPoint destination, String destinationName,
                           long fetchedAt, DataFreshness freshness, String hazardDatasetVersion, String cacheId) {
        this.ranked = Collections.unmodifiableList(new ArrayList<>(ranked));
        this.explanation = Collections.unmodifiableList(new ArrayList<>(explanation));
        this.scenario = scenario;
        this.travelMode = travelMode;
        this.origin = origin;
        this.destination = destination;
        this.destinationName = destinationName;
        this.fetchedAt = fetchedAt;
        this.freshness = freshness;
        this.hazardDatasetVersion = hazardDatasetVersion;
        this.cacheId = cacheId;
    }

    public RouteAssessment getTopRoute() {
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    public List<RouteAssessment> getRanked() { return ranked; }
    public List<String> getExplanation() { return explanation; }
    public FloodScenario getScenario() { return scenario; }
    public TravelMode getTravelMode() { return travelMode; }
    public GeoPoint getOrigin() { return origin; }
    public GeoPoint getDestination() { return destination; }
    public String getDestinationName() { return destinationName; }
    /** When the provider returned these routes (not when they were re-assessed). */
    public long getFetchedAt() { return fetchedAt; }
    /** LIVE for a fresh provider answer, CACHED when re-opened offline. */
    public DataFreshness getFreshness() { return freshness; }
    public String getHazardDatasetVersion() { return hazardDatasetVersion; }
    public String getCacheId() { return cacheId; }
}
