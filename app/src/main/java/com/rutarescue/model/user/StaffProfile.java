package com.rutarescue.model.user;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.LigtasApplication;

/**
 * City staff: a moderator or an LGU officer. Every staff member is locked to the city chosen at
 * staff sign-up (their jurisdiction) and can only see or change that city's records.
 *
 * Inheritance + polymorphism: ModeratorProfile and LguProfile answer the "may I?" questions
 * differently, so screens and services ask the profile instead of checking the role.
 * The Firestore rules enforce the same split on the server.
 *
 * 2026-10-22: only the LGU creates centers, sets the city's alert level and posts road closures;
 * a moderator runs ONE assigned center and sends advisories about it to the LGU for approval.
 */
public abstract class StaffProfile extends UserProfile {

    private final String position;

    protected StaffProfile(String uid, String displayName, String email, MetroManilaCity jurisdiction, String position) {
        super(uid, displayName, email, jurisdiction);
        if (jurisdiction == null) {
            throw new IllegalArgumentException("Staff must have an assigned city.");
        }
        this.position = position == null || position.isBlank() ? "City staff" : position.strip();
    }

    /** Adds, imports, edits, publishes and archives evacuation centers of the city. */
    public abstract boolean canCreateCenters();

    /** Enters the headcount, status and facilities of this center (a moderator's own center). */
    public abstract boolean canManageCenter(EvacuationCenter center);

    /** Approves, rejects or asks for documents on Ligtas applications. */
    public abstract boolean canDecideLigtas();

    /** Publishes centers and advisories so residents can see them (and accepts or declines advisories). */
    public abstract boolean canPublishToResidents();

    /** Approves or rejects staff sign-up requests for the city, and assigns moderators to centers. */
    public abstract boolean canApproveStaff();

    /** Sets the alert level of the whole city. */
    public abstract boolean canSetAlertLevel();

    /** Posts or lifts road closures and bridge/tunnel status (they change residents' routes). */
    public abstract boolean canPostRoadNotices();

    /** Writes advisories about their own center and sends them to the LGU for approval. */
    public abstract boolean canSubmitAdvisories();

    /** Scans a resident's priority ID and records the document check. */
    public abstract boolean canVerifyPriorityIds();

    @Override
    public String getRoleLabel() {
        return position + " • " + getRole().getLabel() + " • " + getJurisdiction().getDisplayName();
    }

    @Override
    public boolean canReview(LigtasApplication application) {
        return canDecideLigtas() && application != null && application.getCity() == getJurisdiction();
    }

    @Override
    public boolean canManage(MetroManilaCity targetCity) {
        return targetCity == getJurisdiction();
    }

    public MetroManilaCity getJurisdiction() {
        return getCity();
    }

    public String getPosition() {
        return position;
    }
}
