package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.repository.AdvisoryRepository;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.service.FreshnessService;

import java.util.ArrayList;
import java.util.List;

/** advisories/{id}: newest first; archived advisories are hidden. */
public final class FirestoreAdvisoryRepository implements AdvisoryRepository {

    private static final String COLLECTION = "advisories";

    private final FirebaseFirestore db;
    private final FreshnessService freshness;
    private final ConnectivityMonitor connectivity;

    public FirestoreAdvisoryRepository(FirebaseFirestore db, FreshnessService freshness, ConnectivityMonitor connectivity) {
        this.db = db;
        this.freshness = freshness;
        this.connectivity = connectivity;
    }

    @Override
    public ListenerHandle listenPublished(Callback<SourcedData<List<Advisory>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("published", true), "LGU advisories", callback);
    }

    @Override
    public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<Advisory>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("city", city.code()), city.getDisplayName() + " advisories", callback);
    }

    private ListenerHandle listen(Query query, String source, Callback<SourcedData<List<Advisory>>> callback) {
        ListenerRegistration registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot == null) return;
            List<Advisory> advisories = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                try {
                    Advisory advisory = FirestoreMappers.advisory(doc);
                    if (!advisory.isArchived()) advisories.add(advisory);
                } catch (ValidationException | RuntimeException ignored) {
                    // A damaged document is skipped instead of hiding every advisory.
                }
            }
            // Sorted here instead of in the query, so no composite index is needed.
            advisories.sort((a, b) -> Long.compare(b.getPublishedAt(), a.getPublishedAt()));
            callback.onSuccess(freshness.wrap(advisories, snapshot.getMetadata().isFromCache(), FreshnessService.ADVISORIES, source));
        });
        return registration::remove;
    }

    @Override
    public void get(String id, Callback<Advisory> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    try {
                        if (!doc.exists()) throw new IllegalStateException("This advisory no longer exists.");
                        callback.onSuccess(FirestoreMappers.advisory(doc));
                    } catch (ValidationException | RuntimeException e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public String newId() {
        return db.collection(COLLECTION).document().getId();
    }

    @Override
    public void save(Advisory advisory, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(advisory.getId()), FirestoreMappers.advisory(advisory, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
