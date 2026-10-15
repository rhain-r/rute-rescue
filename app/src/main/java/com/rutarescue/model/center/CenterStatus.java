package com.rutarescue.model.center;

/**
 * Operating status posted by the LGU. This is NOT live capacity: RutaRescue never claims
 * to know how full a center is (DOCX sections 4 and 14).
 */
public enum CenterStatus {
    OPEN("Open"),
    STANDBY("Standby"),
    CLOSED("Closed"),
    NOT_REPORTED("Status not reported");

    private final String label;

    CenterStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CenterStatus fromName(String name) {
        if (name == null) return NOT_REPORTED;
        for (CenterStatus status : values()) {
            if (status.name().equalsIgnoreCase(name.strip())) return status;
        }
        return NOT_REPORTED;
    }
}
