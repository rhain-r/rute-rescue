package com.rutarescue.model.center;

import java.util.Comparator;
import java.util.List;

/**
 * Where a saved evacuation center is in its life, shown to staff as a chip on every center row
 * and on the center's page, so a newly saved center is easy to find and its next step is clear.
 *
 * The order of the constants is the order of the staff list: centers that still need work
 * come first, published ones after.
 */
public enum CenterState {
    /** Before 2026-10-22 the LGU could return a moderator's center with a note. */
    SENT_BACK("Sent back", "Sent back with a note. Fix the details, then verify and publish it, or archive it."),
    /** Before 2026-10-22 moderators added centers and sent them to the LGU. */
    WAITING_FOR_LGU("Waiting for LGU", "Verified and waiting for the LGU. Residents see it once it is published."),
    /** Saved, but nobody has checked it against a named source yet. */
    NEEDS_VERIFICATION("Needs verification", "Verify it against a named source, then publish it."),
    /** Verified but hidden from residents. */
    DRAFT("Draft", "Verified but hidden from residents. Publish it when it is ready."),
    /** Shown to residents in Centers, on the map and in Safe Route. */
    PUBLISHED("Published", "Residents can see it in Centers, on the map and in Safe Route."),
    /** Removed from every list; kept in the records. */
    ARCHIVED("Archived", "Archived: hidden everywhere, kept in the records.");

    private final String label;
    private final String explanation;

    CenterState(String label, String explanation) {
        this.label = label;
        this.explanation = explanation;
    }

    public static CenterState of(EvacuationCenter center) {
        if (center.isArchived()) return ARCHIVED;
        if (center.isPublished()) return PUBLISHED;
        if (center.isAwaitingLgu()) return WAITING_FOR_LGU;
        if (!center.getLguNote().isEmpty()) return SENT_BACK;
        if (!center.getVerification().isVerified()) return NEEDS_VERIFICATION;
        return DRAFT;
    }

    /** Sorts a staff list: centers that need work first, then A to Z. */
    public static void sortForStaff(List<EvacuationCenter> centers) {
        centers.sort(Comparator.comparing(CenterState::of)
                .thenComparing(EvacuationCenter::getName, String.CASE_INSENSITIVE_ORDER));
    }

    public boolean isVisibleToResidents() {
        return this == PUBLISHED;
    }

    public String getLabel() {
        return label;
    }

    /** One sentence for the center's page. */
    public String getExplanation() {
        return explanation;
    }
}
