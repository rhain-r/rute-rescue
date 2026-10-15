package com.rutarescue.model.route;

/**
 * The name a route card gets in the Phase 1 route comparison.
 * "Lowest risk" replaces the spec's "Safest": RutaRescue never calls a route safe (DOCX).
 */
public enum RouteRole {
    LOWEST_RISK("Lowest risk"),
    BALANCED("Balanced"),
    MOST_DIRECT("Most direct"),
    ALTERNATIVE("Alternative"),
    ONLY_ROUTE("Only route");

    private final String label;

    RouteRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
