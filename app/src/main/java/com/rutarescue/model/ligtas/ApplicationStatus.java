package com.rutarescue.model.ligtas;

/** Life cycle of a Ligtas Priority application. */
public enum ApplicationStatus {
    PENDING("Pending review"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ApplicationStatus fromName(String name) {
        if (name == null) return PENDING;
        for (ApplicationStatus status : values()) {
            if (status.name().equalsIgnoreCase(name.strip())) return status;
        }
        return PENDING;
    }
}
