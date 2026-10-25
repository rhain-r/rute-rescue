package com.rutarescue.model.user;

/** The roles in RutaRescue (DOCX section 3, plus the moderator added on 2026-10-20). */
public enum Role {
    /** Self-registered resident. */
    RESIDENT("Resident"),
    /** City staff who run one evacuation center: headcounts, Ligtas decisions, priority ID checks, advisories for approval. */
    MODERATOR("Moderator"),
    /** City staff who announce and finalize: centers, alert level, closures, advisory approval, staff accounts. */
    LGU("LGU");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Reads the role of a staff record (lgu_staff/{uid}.role). Accounts made before moderators
     * existed have no role and are LGU; a staff record can never be a resident.
     */
    public static Role staffRoleFrom(String text) {
        return "MODERATOR".equalsIgnoreCase(text == null ? "" : text.strip()) ? MODERATOR : LGU;
    }
}
