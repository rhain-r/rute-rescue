package com.rutarescue.repository;

import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;

/** Loads the signed-in person's profile (resident or pre-provisioned LGU staff). */
public interface UserRepository {

    /** Returns an LguProfile when lgu_staff/{uid} exists, otherwise the ResidentProfile. */
    void loadProfile(String uid, Callback<UserProfile> callback);

    /** Registration can only ever create a RESIDENT profile. */
    void createResidentProfile(ResidentProfile profile, Callback<Void> callback);
}
