package com.rutarescue.model.user;

import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.LigtasApplication;

/** A self-registered resident. Residents can only manage their own records. */
public class ResidentProfile extends UserProfile {

    private final long createdAt;

    public ResidentProfile(String uid, String displayName, String email, MetroManilaCity city, long createdAt) {
        super(uid, displayName, email, city);
        this.createdAt = createdAt;
    }

    @Override
    public Role getRole() {
        return Role.RESIDENT;
    }

    @Override
    public String getRoleLabel() {
        return "Resident";
    }

    @Override
    public boolean canReview(LigtasApplication application) {
        return false;
    }

    @Override
    public boolean canManage(MetroManilaCity targetCity) {
        return false;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
