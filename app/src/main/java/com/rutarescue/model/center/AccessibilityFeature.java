package com.rutarescue.model.center;

/** Facilities an LGU can record for a center (Phase 1 "Center capacity" checkboxes). */
public enum AccessibilityFeature {
    PWD_ACCESSIBLE("PWD accessible"),
    SENIOR_FRIENDLY("Senior friendly"),
    ISOLATION_ROOM("Isolation room"),
    LACTATION_STATION("Lactation station");

    private final String label;

    AccessibilityFeature(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Reads a saved name; names from the first version of the app map to the closest facility. */
    public static AccessibilityFeature fromName(String name) {
        if (name == null) return null;
        String key = name.strip().toUpperCase(java.util.Locale.ROOT);
        for (AccessibilityFeature feature : values()) {
            if (feature.name().equals(key)) return feature;
        }
        switch (key) {
            case "ACCESSIBLE_ENTRANCE":
            case "ACCESSIBLE_TOILET":
                return PWD_ACCESSIBLE;
            case "PRIVATE_AREA":
                return LACTATION_STATION;
            default:
                return null;
        }
    }
}
