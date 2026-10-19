package com.rutarescue.ui.map;

import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.SafeLog;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Loads the NOAH map overlays for MapController without freezing the screen.
 *
 * Each overlay is a 5-7 MB GeoJSON file. It is read and hash-checked ONCE on the io() pool;
 * only the finished text is handed to the UI thread, where MapLibre parses it on its own
 * worker thread. Turning a flood switch on and off again does not read the file again.
 *
 * Plain Java (no MapLibre types), so the unit tests can load the real overlays under stress.
 */
public final class FloodOverlayLoader {

    /** Receives a loaded overlay on the UI thread. */
    public interface Sink {
        void onOverlay(FloodScenario scenario, String geoJson);
    }

    private static final String TAG = "RR-Map";

    private final HazardRepository hazards;
    private final AppExecutors executors;
    private final Sink sink;
    /** Scenarios loaded or on their way. Only used on the UI thread, so it needs no lock. */
    private final EnumSet<FloodScenario> requested = EnumSet.noneOf(FloodScenario.class);

    public FloodOverlayLoader(HazardRepository hazards, AppExecutors executors, Sink sink) {
        this.hazards = hazards;
        this.executors = executors;
        this.sink = sink;
    }

    /** Call on the UI thread. Starts a background read unless the overlay is loaded or loading. */
    public void request(FloodScenario scenario) {
        if (!requested.add(scenario)) return;
        executors.io().execute(() -> {
            try {
                String geoJson = hazards.loadGeoJsonBlocking(scenario);
                executors.main().execute(() -> sink.onOverlay(scenario, geoJson));
            } catch (IOException error) {
                SafeLog.w(TAG, "Could not load the " + scenario.getLabel() + " overlay", error);
                executors.main().execute(() -> requested.remove(scenario)); // the next request tries again
            }
        });
    }

    /** True once the overlay was asked for (it may still be loading). */
    public boolean isRequested(FloodScenario scenario) {
        return requested.contains(scenario);
    }
}
