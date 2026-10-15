package com.rutarescue.model.user;

import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.LigtasApplication;

/**
 * Pre-provisioned LGU personnel. Their profile comes from the lgu_staff collection, which
 * only a project administrator can write (from the Firebase console).
 */
public class LguProfile extends UserProfile {

    private final String position;

    public LguProfile(String uid, String displayName, String email, MetroManilaCity jurisdiction, String position) {
        super(uid, displayName, email, jurisdiction);
        if (jurisdiction == null) {
            throw new IllegalArgumentException("LGU staff must have an assigned jurisdiction.");
        }
        this.position = position == null || position.isBlank() ? "LGU personnel" : position.strip();
    }

    @Override
    public Role getRole() {
        return Role.LGU;
    }

    @Override
    public String getRoleLabel() {
        return position + " • " + getJurisdiction().getDisplayName();
    }

    @Override
    public boolean canReview(LigtasApplication application) {
        return application != null && getJurisdiction() == application.getCity();
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
