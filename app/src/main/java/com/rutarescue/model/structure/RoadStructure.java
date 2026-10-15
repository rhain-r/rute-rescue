package com.rutarescue.model.structure;

/**
 * What kind of road the traveller is on at a sampled point (DOCX section 8). This is
 * decided BEFORE any flood class is applied.
 */
public enum RoadStructure {
    SURFACE("Surface road"),
    BRIDGE("Bridge / flyover"),
    TUNNEL("Tunnel / underpass"),
    UNKNOWN("Unconfirmed structure");

    private final String label;

    RoadStructure(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
