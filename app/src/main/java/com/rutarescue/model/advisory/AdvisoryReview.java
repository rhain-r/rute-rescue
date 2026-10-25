package com.rutarescue.model.advisory;

/**
 * Where a moderator's advisory is in the LGU approval (2026-10-22):
 * PENDING -> APPROVED (published to residents) or PENDING -> DECLINED (with the LGU's comment).
 * NONE is an advisory the LGU wrote itself, which needs no approval.
 */
public enum AdvisoryReview {
    NONE("LGU advisory"),
    PENDING("Pending approval"),
    APPROVED("Approved"),
    DECLINED("Declined");

    private final String label;

    AdvisoryReview(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Reads the stored state. Advisories saved before 2026-10-22 have no "review" field: a draft
     * waiting for the LGU is PENDING, one the LGU sent back with a note is DECLINED, and a
     * published moderator draft is APPROVED.
     */
    public static AdvisoryReview fromStored(String stored, boolean awaitingLgu, String lguNote, boolean published,
                                            String requestedBy) {
        if (stored != null && !stored.isBlank()) {
            for (AdvisoryReview review : values()) {
                if (review.name().equalsIgnoreCase(stored.strip())) return review;
            }
        }
        boolean fromModerator = requestedBy != null && !requestedBy.isBlank();
        if (awaitingLgu && !published) return PENDING;
        if (!published && lguNote != null && !lguNote.isBlank()) return DECLINED;
        if (published && fromModerator) return APPROVED;
        return NONE;
    }
}
