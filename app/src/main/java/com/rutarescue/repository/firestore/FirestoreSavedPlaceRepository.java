package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.place.SavedPlace;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.SavedPlaceRepository;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * users/{uid}/places/{id}. Moving saved places under the user document (instead of the
 * legacy shared "locations" collection) lets the rules restrict them to their owner.
 */
public final class FirestoreSavedPlaceRepository implements SavedPlaceRepository {

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreSavedPlaceRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public ListenerHandle listen(String uid, Callback<List<SavedPlace>> callback) {
        ListenerRegistration registration = db.collection("users").document(uid).collection("places")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null) return;
                    List<SavedPlace> places = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try {
                            places.add(new SavedPlace(doc.getId(), FirestoreMappers.str(doc, "name"),
                                    new GeoPoint(FirestoreMappers.dbl(doc, "lat"), FirestoreMappers.dbl(doc, "lon")),
                                    FirestoreMappers.num(doc, "createdAt")));
                        } catch (ValidationException | RuntimeException ignored) {
                            // skip malformed entries
                        }
                    }
                    places.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    callback.onSuccess(places);
                });
        return registration::remove;
    }

    @Override
    public void add(String uid, SavedPlace place, Callback<Void> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", place.getDisplayName());
        map.put("lat", place.getLocation().getLatitude());
        map.put("lon", place.getLocation().getLongitude());
        map.put("createdAt", place.getCreatedAt());
        FirestoreWrites.complete(db.collection("users").document(uid).collection("places").document().set(map),
                connectivity, callback);
    }

    @Override
    public void delete(String uid, String placeId, Callback<Void> callback) {
        FirestoreWrites.complete(db.collection("users").document(uid).collection("places").document(placeId).delete(),
                connectivity, callback);
    }
}
