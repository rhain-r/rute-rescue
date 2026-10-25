package com.rutarescue.model.user;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.MetroManilaCity;

/**
 * Moderator: the city staff member in charge of ONE evacuation center (2026-10-22). The LGU
 * assigns the center when it approves the sign-up (or later). A moderator updates that center's
 * headcount, status and facilities, decides Ligtas applications, checks residents' priority IDs,
 * and sends advisories about the center to the LGU, who approves (publishes) or declines them.
 * A moderator never adds centers, sets the alert level or posts road closures.
 */
public class ModeratorProfile extends StaffProfile {

    /** The center this moderator runs ("" until the LGU assigns one). */
    private final String centerId;
    private final String centerName;

    public ModeratorProfile(String uid, String displayName, String email, MetroManilaCity jurisdiction, String position) {
        this(uid, displayName, email, jurisdiction, position, "", "");
    }

    public ModeratorProfile(String uid, String displayName, String email, MetroManilaCity jurisdiction, String position,
                            String centerId, String centerName) {
        super(uid, displayName, email, jurisdiction, position);
        this.centerId = centerId == null ? "" : centerId.strip();
        this.centerName = centerName == null ? "" : centerName.strip();
    }

    @Override
    public Role getRole() {
        return Role.MODERATOR;
    }

    @Override
    public String getRoleLabel() {
        return super.getRoleLabel() + (hasAssignedCenter() ? " • " + centerName : "");
    }

    @Override
    public boolean canCreateCenters() {
        return false;
    }

    /** Only the assigned center, and only in the moderator's own city. */
    @Override
    public boolean canManageCenter(EvacuationCenter center) {
        return center != null && hasAssignedCenter() && centerId.equals(center.getId())
                && center.getCity() == getJurisdiction();
    }

    @Override
    public boolean canDecideLigtas() {
        return true;
    }

    @Override
    public boolean canPublishToResidents() {
        return false;
    }

    @Override
    public boolean canApproveStaff() {
        return false;
    }

    @Override
    public boolean canSetAlertLevel() {
        return false;
    }

    @Override
    public boolean canPostRoadNotices() {
        return false;
    }

    /** Advisories are about the moderator's own center, so they need one first. */
    @Override
    public boolean canSubmitAdvisories() {
        return hasAssignedCenter();
    }

    @Override
    public boolean canVerifyPriorityIds() {
        return true;
    }

    public boolean hasAssignedCenter() {
        return !centerId.isEmpty();
    }

    public String getCenterId() {
        return centerId;
    }

    public String getCenterName() {
        return centerName;
    }
}
