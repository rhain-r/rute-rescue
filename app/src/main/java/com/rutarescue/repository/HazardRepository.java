package com.rutarescue.repository;

import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.persistence.PreparedDataManifest;
import com.rutarescue.risk.HazardLookup;

import java.io.IOException;

/** Prepared NOAH data bundled with the app. Works fully offline. */
public interface HazardRepository {

    /** Loads (and caches) the hazard grid of a scenario. Call from a background thread. */
    HazardLookup loadBlocking(FloodScenario scenario) throws IOException;

    /** Reads the scenario's display GeoJSON (checked against the manifest). Call from a background thread. */
    String loadGeoJsonBlocking(FloodScenario scenario) throws IOException;

    PreparedDataManifest getManifest();
}
