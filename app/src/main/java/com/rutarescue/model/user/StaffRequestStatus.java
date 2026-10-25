package com.rutarescue.model.user;

/** Life cycle of a staff sign-up request: waiting, then approved or rejected by the city's LGU. */
public enum StaffRequestStatus {
    PENDING("Waiting for approval"),
    APPROVED("Approved"),
    REJECTED("Not approved");

    private final String label;

    StaffRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static StaffRequestStatus fromName(String name) {
        for (StaffRequestStatus status : values()) {
            if (status.name().equalsIgnoreCase(name == null ? "" : name.strip())) return status;
        }
        return PENDING;
    }
}
