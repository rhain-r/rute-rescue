package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.exception.ProfileNotFoundException;
import com.rutarescue.exception.StaffPendingException;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Profiles live in three collections:
 *   lgu_staff/{uid}     - approved staff (role MODERATOR or LGU; no role = LGU). Created by the
 *                         city's LGU when it approves a sign-up, or by an administrator in the console
 *   staffRequests/{uid} - a staff sign-up still waiting for (or refused by) the city's LGU
 *   users/{uid}         - created by resident self-registration, role is always RESIDENT
 * The security rules make sure nobody can make themselves staff or change their role.
 */
public final class FirestoreUserRepository implements UserRepository {

    private final FirebaseFirestore db;

    public FirestoreUserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void loadProfile(String uid, Callback<UserProfile> callback) {
        db.collection("lgu_staff").document(uid).get()
                .addOnSuccessListener(lguDoc -> {
                    if (lguDoc.exists()) {
                        deliverLgu(uid, lguDoc, callback);
                    } else {
                        checkStaffRequest(uid, callback);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void deliverLgu(String uid, DocumentSnapshot doc, Callback<UserProfile> callback) {
        StaffProfile staff = FirestoreStaffRepository.staff(doc);
        if (staff == null) {
            callback.onError(new IllegalStateException(
                    "This staff account has no valid city. Ask the administrator to set lgu_staff/" + uid + ".jurisdiction."));
            return;
        }
        callback.onSuccess(staff);
    }

    /** No staff record: a staff sign-up waiting for approval, or else a resident. */
    private void checkStaffRequest(String uid, Callback<UserProfile> callback) {
        db.collection(FirestoreStaffRepository.REQUESTS).document(uid).get()
                .addOnSuccessListener(requestDoc -> {
                    if (requestDoc.exists()) {
                        callback.onError(new StaffPendingException(FirestoreStaffRepository.request(requestDoc)));
                    } else {
                        loadResident(uid, callback);
                    }
                })
                // A database still on the older rules cannot read staff requests: residents must still
                // be able to sign in, so carry on with the resident profile.
                .addOnFailureListener(error -> loadResident(uid, callback));
    }

    private void loadResident(String uid, Callback<UserProfile> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError(new ProfileNotFoundException());
                        return;
                    }
                    callback.onSuccess(new ResidentProfile(uid, FirestoreMappers.str(doc, "displayName"),
                            FirestoreMappers.str(doc, "email"), MetroManilaCity.fromCode(FirestoreMappers.str(doc, "city")),
                            FirestoreMappers.num(doc, "createdAt")));
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void createResidentProfile(ResidentProfile profile, Callback<Void> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put("displayName", profile.getDisplayName());
        map.put("email", profile.getEmail());
        map.put("city", profile.getCity() == null ? null : profile.getCity().code());
        map.put("role", "RESIDENT");
        map.put("createdAt", profile.getCreatedAt());
        db.collection("users").document(profile.getUid()).set(map)
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
