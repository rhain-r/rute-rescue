package com.rutarescue.risk;

import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;

/**
 * Answers "what NOAH hazard class is mapped at this point?" for one scenario.
 *
 * Polymorphism: the app uses HazardGrid (read from prepared assets), while unit tests use
 * a tiny fake grid. RiskEngine works with either because it only knows this interface.
 */
public interface HazardLookup {

    FloodScenario getScenario();

    /** Version label from the data manifest, e.g. "noah-5yr-v1". */
    String getDatasetVersion();

    /** False when the point is outside the area covered by the prepared NOAH data. */
    boolean covers(double latitude, double longitude);

    /** Mapped class at the point; NONE when covered but not inside any hazard polygon. */
    HazardClass classify(double latitude, double longitude);
}
