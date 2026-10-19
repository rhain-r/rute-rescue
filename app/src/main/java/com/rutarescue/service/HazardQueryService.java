package com.rutarescue.service;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.PointHazard;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.risk.HazardLookup;
import com.rutarescue.util.AppExecutors;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Answers "which NOAH hazard class is mapped here?" for the LGU map's tap-to-inspect without
 * freezing the screen, however fast the officer taps or switches scenarios.
 *
 *  - The grid is loaded and read on the io() pool; only the small finished answer is posted
 *    to the UI thread.
 *  - The newest request wins. Every request gets a ticket number. A queued request that a
 *    newer tap has already replaced is skipped before it does any work, and an answer that
 *    arrives after a newer tap is dropped, so an old answer never overwrites the newest one.
 *
 * Each map screen creates its own instance, so two screens never cancel each other.
 */
public final class HazardQueryService {

    private final HazardRepository hazards;
    private final AppExecutors executors;
    /** Ticket of the newest request; any smaller ticket is out of date. */
    private final AtomicLong newestTicket = new AtomicLong();

    public HazardQueryService(HazardRepository hazards, AppExecutors executors) {
        this.hazards = hazards;
        this.executors = executors;
    }

    /** Looks up the point in the background; the callback runs on the UI thread. */
    public void inspect(GeoPoint point, FloodScenario scenario, Callback<PointHazard> callback) {
        long ticket = newestTicket.incrementAndGet();
        executors.io().execute(() -> {
            if (ticket != newestTicket.get()) return; // a newer tap arrived while this one waited
            try {
                HazardLookup lookup = hazards.loadBlocking(scenario);
                double lat = point.getLatitude();
                double lon = point.getLongitude();
                PointHazard answer = new PointHazard(point, scenario, lookup.covers(lat, lon), lookup.classify(lat, lon));
                deliver(ticket, callback, answer, null);
            } catch (Exception error) {
                deliver(ticket, callback, null, error);
            }
        });
    }

    /** Forgets every request still in progress (for example when the screen closes). */
    public void cancel() {
        newestTicket.incrementAndGet();
    }

    private void deliver(long ticket, Callback<PointHazard> callback, PointHazard answer, Exception error) {
        executors.main().execute(() -> {
            if (ticket != newestTicket.get()) return; // replaced by a newer request: drop it
            if (error == null) callback.onSuccess(answer); else callback.onError(error);
        });
    }
}
