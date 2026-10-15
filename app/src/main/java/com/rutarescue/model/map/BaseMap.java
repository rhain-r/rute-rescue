package com.rutarescue.model.map;

/** The three base maps in the Phase 1 "Map layers" sheet. */
public enum BaseMap {
    STANDARD("Standard"),
    TOPOGRAPHIC("Topographic"),
    SATELLITE("Satellite");

    private final String label;

    BaseMap(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static BaseMap fromName(String name) {
        for (BaseMap map : values()) {
            if (map.name().equalsIgnoreCase(name)) return map;
        }
        return STANDARD;
    }
}
