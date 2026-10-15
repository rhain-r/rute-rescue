package com.rutarescue.model.structure;

/** Passability evidence for a bridge or tunnel, set by LGU staff. */
public enum StructureStatus {
    UNVERIFIED("Passability unverified"),
    VERIFIED_OPEN("Verified open"),
    CLOSED("Closed / impassable");

    private final String label;

    StructureStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static StructureStatus fromName(String name) {
        if (name != null) {
            for (StructureStatus status : values()) {
                if (status.name().equalsIgnoreCase(name.strip())) return status;
            }
        }
        return UNVERIFIED;
    }
}
