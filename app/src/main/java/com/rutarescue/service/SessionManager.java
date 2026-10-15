package com.rutarescue.service;

import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.UserRepository;

/**
 * Remembers who is signed in for the lifetime of the app process. Firebase keeps the
 * login itself, and Firestore's offline cache lets the profile load without internet.
 */
public final class SessionManager {

    private final AuthService auth;
    private final UserRepository users;
    private volatile UserProfile profile;

    public SessionManager(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    /** Loads the profile of an existing Firebase login; delivers null when signed out. */
    public void restore(Callback<UserProfile> callback) {
        if (!auth.isSignedIn()) {
            profile = null;
            callback.onSuccess(null);
            return;
        }
        users.loadProfile(auth.currentUid(), new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile loaded) {
                profile = loaded;
                callback.onSuccess(loaded);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    public void setProfile(UserProfile signedIn) {
        profile = signedIn;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public boolean isLgu() {
        return profile instanceof LguProfile;
    }

    /** For LGU-only screens; the database rules enforce the same restriction server-side. */
    public LguProfile requireLgu() {
        if (!(profile instanceof LguProfile)) {
            throw new IllegalStateException("This action is only available to LGU personnel.");
        }
        return (LguProfile) profile;
    }

    public void signOut() {
        auth.signOut();
        profile = null;
    }
}
