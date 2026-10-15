package com.rutarescue.model.center;

/** Accessibility and support features an LGU can record for a center. */
public enum AccessibilityFeature {
    ACCESSIBLE_ENTRANCE("Accessible entrance"),
    ACCESSIBLE_TOILET("Accessible toilet"),
    MEDICAL_DESK("Medical desk"),
    FAMILY_AREA("Family area"),
    PRIVATE_AREA("Breastfeeding / private area");

    private final String label;

    AccessibilityFeature(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AccessibilityFeature fromName(String name) {
        if (name == null) return null;
        for (AccessibilityFeature feature : values()) {
            if (feature.name().equalsIgnoreCase(name.strip())) return feature;
        }
        return null;
    }
}
