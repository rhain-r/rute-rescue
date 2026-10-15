package com.rutarescue.model.hazard;

/** Kinds of versioned datasets managed on the LGU "Data & Centers" screen. */
public enum DatasetType {
    FLOOD_HAZARD("Flood-risk zones (Project NOAH)"),
    EVACUATION_CENTERS("Evacuation centers"),
    ROAD_STRUCTURES("Road network: bridges and tunnels");

    private final String label;

    DatasetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DatasetType fromName(String name) {
        if (name != null) {
            for (DatasetType type : values()) {
                if (type.name().equalsIgnoreCase(name.strip())) return type;
            }
        }
        return EVACUATION_CENTERS;
    }
}
