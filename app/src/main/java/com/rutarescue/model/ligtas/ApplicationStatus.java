package com.rutarescue.model.ligtas;

/** Life cycle of a Ligtas Priority application. */
public enum ApplicationStatus {
    PENDING("Pending review"),
    /** 2026-10-20: the moderator asks the resident for missing or clearer documents. */
    NEEDS_DOCUMENTS("Needs documents"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True for the statuses a moderator can give: approved, needs documents, rejected. */
    public boolean isDecision() {
        return this != PENDING;
    }

    public static ApplicationStatus fromName(String name) {
        if (name == null) return PENDING;
        for (ApplicationStatus status : values()) {
            if (status.name().equalsIgnoreCase(name.strip())) return status;
        }
        return PENDING;
    }
}
