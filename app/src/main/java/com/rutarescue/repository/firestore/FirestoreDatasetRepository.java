package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.hazard.DatasetVersion;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.DatasetRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.List;

/** datasetVersions/{id}: version history of LGU-managed datasets. */
public final class FirestoreDatasetRepository implements DatasetRepository {

    private static final String COLLECTION = "datasetVersions";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreDatasetRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public String newId() {
        return db.collection(COLLECTION).document().getId();
    }

    @Override
    public ListenerHandle listen(Callback<List<DatasetVersion>> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot == null) return;
            List<DatasetVersion> versions = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                try {
                    versions.add(FirestoreMappers.dataset(doc));
                } catch (RuntimeException ignored) {
                    // skip malformed entries
                }
            }
            versions.sort((a, b) -> Long.compare(
                    b.getAuditInfo() == null ? 0 : b.getAuditInfo().getUpdatedAt(),
                    a.getAuditInfo() == null ? 0 : a.getAuditInfo().getUpdatedAt()));
            callback.onSuccess(versions);
        });
        return registration::remove;
    }

    @Override
    public void save(DatasetVersion version, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(version.getId()), FirestoreMappers.dataset(version, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
