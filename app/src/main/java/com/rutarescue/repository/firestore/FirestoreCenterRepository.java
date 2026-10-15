package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CenterRepository;
import com.rutarescue.repository.CenterSnapshot;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.service.FreshnessService;
import com.rutarescue.util.SafeLog;

import java.util.ArrayList;
import java.util.List;

/** centers/{id}: published centers are readable by every signed-in user; LGU staff write. */
public final class FirestoreCenterRepository implements CenterRepository {

    private static final String TAG = "RR-Centers";
    private static final String COLLECTION = "centers";

    private final FirebaseFirestore db;
    private final FreshnessService freshness;
    private final ConnectivityMonitor connectivity;

    public FirestoreCenterRepository(FirebaseFirestore db, FreshnessService freshness, ConnectivityMonitor connectivity) {
        this.db = db;
        this.freshness = freshness;
        this.connectivity = connectivity;
    }

    @Override
    public ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("published", true), "LGU-published centers", callback);
    }

    @Override
    public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<EvacuationCenter>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("city", city.code()), city.getDisplayName() + " centers", callback);
    }

    private ListenerHandle listen(Query query, String source, Callback<SourcedData<List<EvacuationCenter>>> callback) {
        ListenerRegistration registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot == null) return;
            List<EvacuationCenter> centers = new ArrayList<>();
            int skipped = 0;
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                try {
                    EvacuationCenter center = FirestoreMappers.center(doc);
                    if (!center.isArchived()) centers.add(center);
                } catch (ValidationException | RuntimeException damaged) {
                    skipped++;
                }
            }
            if (skipped > 0) SafeLog.w(TAG, "Skipped " + skipped + " invalid center documents");
            callback.onSuccess(freshness.wrap(centers, snapshot.getMetadata().isFromCache(), FreshnessService.CENTERS, source));
        });
        return registration::remove;
    }

    @Override
    public void get(String id, Callback<EvacuationCenter> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    try {
                        if (!doc.exists()) throw new IllegalStateException("This center no longer exists.");
                        callback.onSuccess(FirestoreMappers.center(doc));
                    } catch (ValidationException | RuntimeException e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public ListenerHandle listen(String id, Callback<CenterSnapshot> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).document(id)
                .addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        callback.onError(new IllegalStateException("This center no longer exists."));
                        return;
                    }
                    try {
                        callback.onSuccess(new CenterSnapshot(FirestoreMappers.center(doc),
                                doc.getMetadata().hasPendingWrites()));
                    } catch (ValidationException | RuntimeException damaged) {
                        callback.onError(damaged);
                    }
                });
        return registration::remove;
    }

    @Override
    public String newId() {
        return db.collection(COLLECTION).document().getId();
    }

    @Override
    public void save(EvacuationCenter center, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(center.getId()), FirestoreMappers.center(center, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }

    @Override
    public void saveAll(List<EvacuationCenter> centers, List<AuditEvent> audits, Callback<Void> callback) {
        if (centers.size() != audits.size()) {
            callback.onError(new IllegalArgumentException("Each center needs exactly one audit event."));
            return;
        }
        if (centers.size() > 200) {
            callback.onError(new IllegalArgumentException("Import at most 200 centers at a time."));
            return;
        }
        WriteBatch batch = db.batch();
        for (int i = 0; i < centers.size(); i++) {
            EvacuationCenter center = centers.get(i);
            AuditEvent audit = audits.get(i);
            batch.set(db.collection(COLLECTION).document(center.getId()), FirestoreMappers.center(center, audit.getId()));
            batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        }
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
