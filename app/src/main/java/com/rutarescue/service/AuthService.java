package com.rutarescue.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.rutarescue.exception.ProfileNotFoundException;
import com.rutarescue.exception.StaffPendingException;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.Role;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.StaffRepository;
import com.rutarescue.repository.UserRepository;
import com.rutarescue.validation.AccountValidator;
import com.rutarescue.validation.NameFormatter;

/**
 * Sign-in and registration with Firebase Authentication.
 * Resident registration always creates a RESIDENT. Staff sign-up (2026-10-20) only creates a
 * locked PENDING request: the account becomes a moderator or LGU officer when that city's LGU
 * approves it. The security rules enforce both.
 */
public final class AuthService {

    private final FirebaseAuth auth;
    private final UserRepository users;
    private final StaffRepository staff;

    public AuthService(FirebaseAuth auth, UserRepository users, StaffRepository staff) {
        this.auth = auth;
        this.users = users;
        this.staff = staff;
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
        signInWith(checkedEmail, password, callback, true);
    }

    /**
     * @param retryTrimmed a pasted or autofilled password can carry a space before or after it; when the
     *                     typed password fails, try once more without those spaces
     */
    private void signInWith(String checkedEmail, String password, Callback<UserProfile> callback, boolean retryTrimmed) {
        auth.signInWithEmailAndPassword(checkedEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Sign-in did not return a user."));
                    } else {
                        users.loadProfile(user.getUid(), callback);
                    }
                })
                .addOnFailureListener(error -> {
                    String trimmed = password.strip();
                    if (retryTrimmed && error instanceof FirebaseAuthInvalidCredentialsException
                            && !trimmed.isEmpty() && !trimmed.equals(password)) {
                        signInWith(checkedEmail, trimmed, callback, false);
                    } else {
                        callback.onError(error);
                    }
                });
    }

    /** Sends Firebase's password-reset e-mail (works for real inboxes; .test addresses receive nothing). */
    public void sendPasswordReset(String email, Callback<Void> callback) {
        String checkedEmail;
        try {
            checkedEmail = AccountValidator.requireEmail(email);
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        auth.sendPasswordResetEmail(checkedEmail)
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
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

    /**
     * Staff sign-up: creates the login and a PENDING request for the chosen role and city, then
     * signs out again, because the account cannot be used until the city's LGU approves it.
     */
    public void registerStaff(String fullName, String email, String password, String confirmPassword, Role role,
                              MetroManilaCity city, String position, Callback<StaffRequest> callback) {
        String checkedEmail;
        try {
            // Check the whole form before any account is created.
            checkedEmail = AccountValidator.requireEmail(email);
            StaffRequest.create("check", fullName, email, role, city, position, System.currentTimeMillis());
            AccountValidator.requirePassword(password);
            if (!password.equals(confirmPassword)) {
                throw new ValidationException("confirmPassword", "The passwords do not match.");
            }
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        auth.createUserWithEmailAndPassword(checkedEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Sign-up did not return a user."));
                        return;
                    }
                    submitStaffRequest(user.getUid(), fullName, email, role, city, position, callback);
                })
                .addOnFailureListener(error -> {
                    if (error instanceof FirebaseAuthUserCollisionException) {
                        resumeStaffSignUp(checkedEmail, password, fullName, role, city, position, callback);
                    } else {
                        callback.onError(error);
                    }
                });
    }

    /**
     * The e-mail already has a login. With the same password this is usually an earlier staff sign-up
     * that was interrupted after the login was made (no request was saved): finish it instead of
     * saying "already exists" forever. An existing resident or staff account is never changed.
     */
    private void resumeStaffSignUp(String checkedEmail, String password, String fullName, Role role, MetroManilaCity city,
                                   String position, Callback<StaffRequest> callback) {
        auth.signInWithEmailAndPassword(checkedEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Sign-up did not return a user."));
                        return;
                    }
                    users.loadProfile(user.getUid(), new Callback<UserProfile>() {
                        @Override
                        public void onSuccess(UserProfile existing) {
                            auth.signOut();
                            callback.onError(new ValidationException("email", existing instanceof StaffProfile
                                    ? "This e-mail is already an approved staff account. Sign in instead."
                                    : "This e-mail already has a resident account. Use another e-mail for staff."));
                        }

                        @Override
                        public void onError(Exception error) {
                            if (error instanceof StaffPendingException) {
                                auth.signOut();
                                callback.onSuccess(((StaffPendingException) error).getRequest()); // already sent
                            } else if (error instanceof ProfileNotFoundException) {
                                submitStaffRequest(user.getUid(), fullName, checkedEmail, role, city, position, callback);
                            } else {
                                auth.signOut();
                                callback.onError(error);
                            }
                        }
                    });
                })
                .addOnFailureListener(error -> callback.onError(new ValidationException("password",
                        "An account with this e-mail already exists with a different password. Sign in with that "
                                + "password, or use another e-mail.")));
    }

    /** Saves the PENDING request for the signed-in login, then signs out (the account stays locked). */
    private void submitStaffRequest(String uid, String fullName, String email, Role role, MetroManilaCity city,
                                    String position, Callback<StaffRequest> callback) {
        StaffRequest request;
        try {
            request = StaffRequest.create(uid, fullName, email, role, city, position, System.currentTimeMillis());
        } catch (ValidationException invalid) {
            auth.signOut();
            callback.onError(invalid);
            return;
        }
        staff.submitRequest(request, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                auth.signOut();
                callback.onSuccess(request);
            }

            @Override
            public void onError(Exception error) {
                auth.signOut();
                boolean refused = error instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED;
                callback.onError(refused
                        ? new IllegalStateException("Staff sign-up is not switched on in this RutaRescue database yet "
                        + "(its security rules are older). Ask the administrator to publish the latest rules, then send "
                        + "the request again with the same e-mail and password.")
                        : error);
            }
        });
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
