package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.LigtasRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.service.FreshnessService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ligtasApplications/{id}. Queries always filter by owner or by city because the security
 * rules only allow reads that are guaranteed to stay within those limits.
 */
public final class FirestoreLigtasRepository implements LigtasRepository {

    private static final String COLLECTION = "ligtasApplications";

    private final FirebaseFirestore db;
    private final FreshnessService freshness;
    private final ConnectivityMonitor connectivity;

    public FirestoreLigtasRepository(FirebaseFirestore db, FreshnessService freshness, ConnectivityMonitor connectivity) {
        this.db = db;
        this.freshness = freshness;
        this.connectivity = connectivity;
    }

    @Override
    public String newId() {
        return db.collection(COLLECTION).document().getId();
    }

    @Override
    public ListenerHandle listenMine(String ownerUid, Callback<SourcedData<List<LigtasApplication>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("ownerUid", ownerUid), "Your applications", callback);
    }

    @Override
    public ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<LigtasApplication>>> callback) {
        return listen(db.collection(COLLECTION).whereEqualTo("city", city.code()), city.getDisplayName() + " applications", callback);
    }

    private ListenerHandle listen(Query query, String source, Callback<SourcedData<List<LigtasApplication>>> callback) {
        ListenerRegistration registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot == null) return;
            List<LigtasApplication> applications = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                applications.add(FirestoreMappers.ligtas(doc));
            }
            applications.sort((a, b) -> Long.compare(b.getSubmittedAt(), a.getSubmittedAt()));
            callback.onSuccess(freshness.wrap(applications, snapshot.getMetadata().isFromCache(), FreshnessService.LIGTAS, source));
        });
        return registration::remove;
    }

    @Override
    public ListenerHandle listenOne(String id, Callback<SourcedData<LigtasApplication>> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).document(id)
                .addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        callback.onError(new IllegalStateException("Application not found or not accessible to your account."));
                        return;
                    }
                    callback.onSuccess(freshness.wrap(FirestoreMappers.ligtas(doc), doc.getMetadata().isFromCache(),
                            FreshnessService.LIGTAS, "Ligtas application"));
                });
        return registration::remove;
    }

    @Override
    public void submit(LigtasApplication application, Callback<Void> callback) {
        FirestoreWrites.complete(db.collection(COLLECTION).document(application.getId())
                .set(FirestoreMappers.ligtas(application, null)), connectivity, callback);
    }

    @Override
    public void saveDecision(LigtasApplication application, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(application.getId()), FirestoreMappers.ligtas(application, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }

    /**
     * The resident's own applications straight from the server, blocking (for SyncWorker, which
     * runs on a background thread). The rules allow this query because it filters by owner.
     */
    public static List<LigtasApplication> readMineFromServer(FirebaseFirestore db, String ownerUid) throws Exception {
        QuerySnapshot snapshot = Tasks.await(db.collection(COLLECTION).whereEqualTo("ownerUid", ownerUid)
                .get(Source.SERVER), 30, TimeUnit.SECONDS);
        List<LigtasApplication> applications = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) applications.add(FirestoreMappers.ligtas(doc));
        return applications;
    }

    @Override
    public void saveResubmission(LigtasApplication application, Callback<Void> callback) {
        // update() with only these three fields: the rules let the owner change nothing else.
        Map<String, Object> full = FirestoreMappers.ligtas(application, null);
        Map<String, Object> changes = new HashMap<>();
        changes.put("status", full.get("status"));
        changes.put("proofRef", full.get("proofRef"));
        changes.put("history", full.get("history"));
        FirestoreWrites.complete(db.collection(COLLECTION).document(application.getId()).update(changes),
                connectivity, callback);
    }
}
