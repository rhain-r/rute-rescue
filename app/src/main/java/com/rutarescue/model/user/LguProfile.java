package com.rutarescue.model.user;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.MetroManilaCity;

/**
 * LGU officer: the city's announcer and finalizer. Creates and publishes evacuation centers,
 * sets the city's alert level, posts road closures, approves or declines the moderators'
 * advisories, and approves staff sign-ups (assigning each moderator to a center). The first LGU
 * of a city is created by an administrator in the Firebase console (lgu_staff/{uid}).
 */
public class LguProfile extends StaffProfile {

    public LguProfile(String uid, String displayName, String email, MetroManilaCity jurisdiction, String position) {
        super(uid, displayName, email, jurisdiction, position == null || position.isBlank() ? "LGU personnel" : position);
    }

    @Override
    public Role getRole() {
        return Role.LGU;
    }

    @Override
    public boolean canCreateCenters() {
        return true;
    }

    /** Headcounts are entered at the center by its moderator; the LGU sees them read-only. */
    @Override
    public boolean canManageCenter(EvacuationCenter center) {
        return false;
    }

    @Override
    public boolean canDecideLigtas() {
        return false;
    }

    @Override
    public boolean canPublishToResidents() {
        return true;
    }

    @Override
    public boolean canApproveStaff() {
        return true;
    }

    @Override
    public boolean canSetAlertLevel() {
        return true;
    }

    @Override
    public boolean canPostRoadNotices() {
        return true;
    }

    @Override
    public boolean canSubmitAdvisories() {
        return false;
    }

    @Override
    public boolean canVerifyPriorityIds() {
        return false;
    }
}
