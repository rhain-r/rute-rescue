package com.rutarescue.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.UserRepository;
import com.rutarescue.validation.AccountValidator;
import com.rutarescue.validation.NameFormatter;

/**
 * Sign-in and resident registration with Firebase Authentication.
 * Registration always creates a RESIDENT; LGU accounts are pre-provisioned by an
 * administrator (DOCX section 3), and the security rules enforce this.
 */
public final class AuthService {

    private final FirebaseAuth auth;
    private final UserRepository users;

    public AuthService(FirebaseAuth auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    public void signIn(String email, String password, Callback<UserProfile> callback) {
        String checkedEmail;
        try {
            checkedEmail = AccountValidator.requireEmail(email);
            if (password == null || password.isEmpty()) {
                throw new ValidationException("password", "Enter your password.");
            }
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        auth.signInWithEmailAndPassword(checkedEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Sign-in did not return a user."));
                    } else {
                        users.loadProfile(user.getUid(), callback);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void registerResident(String fullName, String email, String password, String confirmPassword,
                                 MetroManilaCity city, Callback<UserProfile> callback) {
        String name;
        String checkedEmail;
        try {
            name = NameFormatter.requirePersonName("fullName", fullName);
            checkedEmail = AccountValidator.requireEmail(email);
            AccountValidator.requirePassword(password);
            if (!password.equals(confirmPassword)) {
                throw new ValidationException("confirmPassword", "The passwords do not match.");
            }
            if (city == null) {
                throw new ValidationException("city", "Choose your city or municipality.");
            }
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        auth.createUserWithEmailAndPassword(checkedEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Registration did not return a user."));
                        return;
                    }
                    ResidentProfile profile = new ResidentProfile(user.getUid(), name, checkedEmail, city,
                            System.currentTimeMillis());
                    users.createResidentProfile(profile, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            callback.onSuccess(profile);
                        }

                        @Override
                        public void onError(Exception error) {
                            callback.onError(error);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public void signOut() {
        auth.signOut();
    }
}
