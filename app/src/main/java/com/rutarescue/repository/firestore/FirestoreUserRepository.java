package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Profiles live in two collections:
 *   lgu_staff/{uid} - created ONLY by an administrator in the Firebase console
 *   users/{uid}     - created by resident self-registration, role is always RESIDENT
 * The security rules make sure a resident can never write lgu_staff or change their role.
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
                        loadResident(uid, callback);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void deliverLgu(String uid, DocumentSnapshot doc, Callback<UserProfile> callback) {
        MetroManilaCity jurisdiction = MetroManilaCity.fromCode(FirestoreMappers.str(doc, "jurisdiction"));
        if (jurisdiction == null) {
            callback.onError(new IllegalStateException(
                    "This LGU account has no valid jurisdiction. Ask the administrator to set lgu_staff/" + uid + ".jurisdiction."));
            return;
        }
        callback.onSuccess(new LguProfile(uid, FirestoreMappers.str(doc, "displayName"),
                FirestoreMappers.str(doc, "email"), jurisdiction, FirestoreMappers.str(doc, "position")));
    }

    private void loadResident(String uid, Callback<UserProfile> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError(new IllegalStateException("No RutaRescue profile was found for this account."));
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
