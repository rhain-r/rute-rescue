package com.rutarescue.model.center;

/**
 * Operating status posted by LGU staff (Phase 1 "Center capacity" status pills).
 * NEAR_CAPACITY and FULL come from the LGU's own headcount; RutaRescue never claims a live
 * count from any other source (DOCX sections 4 and 14).
 */
public enum CenterStatus {
    OPEN("Open"),
    NEAR_CAPACITY("Near capacity"),
    FULL("Full"),
    CLOSED("Closed"),
    UNAVAILABLE("Unavailable"),
    NOT_REPORTED("Status not reported");

    /** The five choices on the status pills (NOT_REPORTED is only the starting value). */
    public static final CenterStatus[] CHOICES = {OPEN, NEAR_CAPACITY, FULL, CLOSED, UNAVAILABLE};

    private final String label;

    CenterStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Residents can still be sent here: the center is open, even if it is filling up. */
    public boolean isAcceptingEvacuees() {
        return this == OPEN || this == NEAR_CAPACITY;
    }

    public static CenterStatus fromName(String name) {
        if (name == null) return NOT_REPORTED;
        for (CenterStatus status : values()) {
            if (status.name().equalsIgnoreCase(name.strip())) return status;
        }
        // Older records may say STANDBY; it never meant "open", so it loads as "not reported".
        return NOT_REPORTED;
    }
}
