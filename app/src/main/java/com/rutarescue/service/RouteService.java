package com.rutarescue.service;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.place.PlaceResult;
import com.rutarescue.model.route.AvoidArea;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.route.RouteComparison;
import com.rutarescue.model.route.TravelMode;
import com.rutarescue.network.AvoidingRouteProvider;
import com.rutarescue.network.CachedRouteProvider;
import com.rutarescue.network.PlaceSearchProvider;
import com.rutarescue.network.RouteProvider;
import com.rutarescue.network.RouteResponse;
import com.rutarescue.persistence.CachedRouteEntry;
import com.rutarescue.persistence.RouteCacheStore;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ClosureRepository;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.repository.local.AssetStructureRepository;
import com.rutarescue.risk.ClosureAvoidance;
import com.rutarescue.risk.ClosureLookup;
import com.rutarescue.risk.HazardLookup;
import com.rutarescue.risk.RiskContext;
import com.rutarescue.risk.RiskEngine;
import com.rutarescue.risk.RouteExplainer;
import com.rutarescue.risk.RouteRanker;
import com.rutarescue.risk.StructureIndex;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.SafeLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Safe Route use case (DOCX section 4): get up to three alternatives, assess each one
 * with the local risk engine IN PARALLEL, rank them and explain the ranking.
 *
 * Online: TomTom answers are cached as raw JSON. Offline: the newest cached answer for
 * the same trip is re-opened and labelled as cached (never as live).
 */
public final class RouteService {

    private static final String TAG = "RR-Route";
    public static final int MAX_ALTERNATIVES = 2; // 1 main route + 2 alternatives = up to 3
    /** Margin around a closed stretch that detours must keep clear of. */
    private static final double CLOSURE_PADDING_METERS = 40;

    private final RouteProvider liveProvider;
    private final CachedRouteProvider cachedProvider;
    private final RouteCacheStore cache;
    private final PlaceSearchProvider searchProvider;
    private final HazardRepository hazards;
    private final AssetStructureRepository structures;
    private final StructureStatusRepository structureStatuses;
    private final ClosureRepository closures;
    private final ConnectivityMonitor connectivity;
    private final AppExecutors executors;
    private final RiskEngine engine = new RiskEngine();
    private final RouteRanker ranker = new RouteRanker();
    private final RouteExplainer explainer = new RouteExplainer();

    public RouteService(RouteProvider liveProvider, CachedRouteProvider cachedProvider, RouteCacheStore cache,
                        PlaceSearchProvider searchProvider, HazardRepository hazards, AssetStructureRepository structures,
                        StructureStatusRepository structureStatuses, ClosureRepository closures,
                        ConnectivityMonitor connectivity, AppExecutors executors) {
        this.liveProvider = liveProvider;
        this.cachedProvider = cachedProvider;
        this.cache = cache;
        this.searchProvider = searchProvider;
        this.hazards = hazards;
        this.structures = structures;
        this.structureStatuses = structureStatuses;
        this.closures = closures;
        this.connectivity = connectivity;
        this.executors = executors;
    }

    /** False for OpenStreetMap's Nominatim, which may only be called when the user presses Search. */
    public boolean allowsSearchAsYouType() {
        return searchProvider.allowsSearchAsYouType();
    }

    /** Destination search (online only). */
    public void search(String query, GeoPoint near, Callback<List<PlaceResult>> callback) {
        if (!connectivity.isOnline()) {
            callback.onError(new OfflineException("Search needs internet. Saved places and evacuation centers still work offline."));
            return;
        }
        executors.io().execute(() -> {
            try {
                post(callback, searchProvider.search(query, near), null);
            } catch (OfflineException | ProviderException e) {
                post(callback, null, e);
            }
        });
    }

    /** Requests routes (live when possible, cached otherwise) and assesses them. */
    public void findRoutes(GeoPoint origin, Locatable destination, TravelMode mode, FloodScenario scenario,
                           Callback<RouteComparison> callback) {
        executors.io().execute(() -> {
            try {
                RouteResponse response = null;
                String cacheId = null;
                if (connectivity.isOnline()) {
                    try {
                        response = liveProvider.requestRoutes(origin, destination.getLocation(), mode, MAX_ALTERNATIVES);
                        cacheId = saveToCache(response, origin, destination, mode);
                    } catch (OfflineException lostConnection) {
                        response = null; // fall through to the cache below
                    }
                }
                if (response == null) {
                    response = cachedProvider.requestRoutes(origin, destination.getLocation(), mode, MAX_ALTERNATIVES);
                    cacheId = response.getCacheId();
                }
                RouteComparison comparison = assess(response, scenario, mode, origin, destination.getLocation(),
                        destination.getDisplayName(), cacheId);
                // Every provider route crosses an LGU closure (e.g. a closed bridge): ask for detours.
                if (response.isLive() && ClosureAvoidance.allClosed(comparison.getRanked())) {
                    RouteResponse withDetours = requestDetours(response, comparison, origin, destination, mode);
                    if (withDetours != null) {
                        comparison = assess(withDetours, scenario, mode, origin, destination.getLocation(),
                                destination.getDisplayName(), saveToCache(withDetours, origin, destination, mode));
                    }
                }
                post(callback, comparison, null);
            } catch (Exception e) {
                post(callback, null, e);
            }
        });
    }

    /** Re-opens a cached answer (works offline) and assesses it with any scenario. */
    public void openCached(String cacheId, FloodScenario scenario, Callback<RouteComparison> callback) {
        executors.io().execute(() -> {
            try {
                CachedRouteEntry entry = cache.find(cacheId);
                if (entry == null) throw new OfflineException("That cached route is no longer available.");
                // Anything re-opened from storage is labelled CACHED with its original fetch time,
                // even when it was fetched a minute ago (we never relabel stored traffic as live).
                RouteResponse response = cachedProvider.fromEntry(entry);
                post(callback, assess(response, scenario, TravelMode.fromName(entry.travelMode),
                        new GeoPoint(entry.originLat, entry.originLon), new GeoPoint(entry.destinationLat, entry.destinationLon),
                        entry.destinationName, entry.id), null);
            } catch (Exception e) {
                post(callback, null, e);
            }
        });
    }

    public void listCached(Callback<List<CachedRouteEntry>> callback) {
        executors.io().execute(() -> post(callback, cache.list(), null));
    }

    /**
     * Asks the provider for routes that avoid the closed stretches (TomTom "avoidAreas").
     * Returns the detours plus the best closed route (so the resident still sees why the usual
     * way is not recommended), or null when no detour could be planned.
     */
    private RouteResponse requestDetours(RouteResponse original, RouteComparison comparison, GeoPoint origin,
                                         Locatable destination, TravelMode mode) {
        if (!(liveProvider instanceof AvoidingRouteProvider)) return null;
        List<AvoidArea> areas = ClosureAvoidance.areasToAvoid(comparison.getRanked(), CLOSURE_PADDING_METERS);
        if (areas.isEmpty()) return null;
        try {
            RouteResponse detours = ((AvoidingRouteProvider) liveProvider).requestRoutesAvoiding(origin,
                    destination.getLocation(), mode, MAX_ALTERNATIVES, areas);
            List<RouteCandidate> combined = new ArrayList<>();
            for (RouteCandidate candidate : detours.getCandidates()) combined.add(candidate.withProviderIndex(combined.size()));
            combined.add(comparison.getRanked().get(0).getCandidate().withProviderIndex(combined.size()));
            return new RouteResponse(combined, detours.getRawJson(), detours.getFetchedAt(), true, null,
                    detours.getProvider());
        } catch (OfflineException | ProviderException noDetour) {
            SafeLog.w(TAG, "Could not plan a detour around the closure", noDetour);
            return null;
        }
    }

    private String saveToCache(RouteResponse response, GeoPoint origin, Locatable destination, TravelMode mode) {
        try {
            CachedRouteEntry entry = new CachedRouteEntry();
            entry.destinationName = destination.getDisplayName();
            entry.originLat = origin.getLatitude();
            entry.originLon = origin.getLongitude();
            entry.destinationLat = destination.getLocation().getLatitude();
            entry.destinationLon = destination.getLocation().getLongitude();
            entry.travelMode = mode.name();
            entry.fetchedAt = response.getFetchedAt();
            entry.trafficConsidered = response.hasLiveTraffic();
            entry.rawProviderJson = response.getRawJson();
            entry.provider = response.getProvider();
            return cache.save(entry).id;
        } catch (Exception e) {
            SafeLog.w(TAG, "Could not cache the route answer", e);
            return null;
        }
    }

    /** Runs the risk engine on every candidate at the same time (one task per route). */
    private RouteComparison assess(RouteResponse response, FloodScenario scenario, TravelMode mode, GeoPoint origin,
                                   GeoPoint destination, String destinationName, String cacheId)
            throws Exception {
        HazardLookup hazard = hazards.loadBlocking(scenario);
        StructureIndex structureIndex = structures.buildIndex(
                structureStatuses == null ? Collections.emptyMap() : structureStatuses.loadBlocking());
        ClosureLookup closureLookup = closures == null ? ClosureLookup.NONE : ClosureLookup.of(closures.loadActiveBlocking());
        RiskContext context = new RiskContext(hazard, structureIndex, closureLookup, System.currentTimeMillis());

        List<Callable<RouteAssessment>> tasks = new ArrayList<>();
        for (RouteCandidate candidate : response.getCandidates()) {
            tasks.add(() -> engine.assess(candidate, context));
        }
        List<RouteAssessment> assessments = new ArrayList<>();
        for (Future<RouteAssessment> future : executors.compute().invokeAll(tasks)) {
            try {
                assessments.add(future.get());
            } catch (ExecutionException failed) {
                throw new IllegalStateException("A route could not be assessed.", failed.getCause());
            }
        }
        List<RouteAssessment> ranked = ranker.rank(assessments);
        // Only TomTom answers carry live traffic; OpenStreetMap routing uses typical speeds.
        boolean trafficLive = response.hasLiveTraffic();
        List<String> explanation = explainer.explain(ranked, trafficLive);
        DataFreshness freshness = response.isLive() ? DataFreshness.LIVE : DataFreshness.CACHED;
        return new RouteComparison(ranked, explanation, scenario, mode, origin, destination, destinationName,
                response.getFetchedAt(), freshness, hazard.getDatasetVersion(), cacheId);
    }

    private <T> void post(Callback<T> callback, T value, Exception error) {
        executors.main().execute(() -> {
            if (error == null) callback.onSuccess(value); else callback.onError(error);
        });
    }
}
