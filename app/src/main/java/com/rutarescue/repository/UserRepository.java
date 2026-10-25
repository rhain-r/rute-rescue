package com.rutarescue.repository;

import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;

/** Loads the signed-in person's profile (resident, moderator or LGU officer). */
public interface UserRepository {

    /**
     * Returns a ModeratorProfile or LguProfile when lgu_staff/{uid} exists, otherwise the
     * ResidentProfile. A staff sign-up not approved yet fails with StaffPendingException.
     */
    void loadProfile(String uid, Callback<UserProfile> callback);

    /** Registration can only ever create a RESIDENT profile. */
    void createResidentProfile(ResidentProfile profile, Callback<Void> callback);
}
