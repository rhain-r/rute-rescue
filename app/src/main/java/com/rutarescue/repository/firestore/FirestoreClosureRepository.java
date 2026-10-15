package com.rutarescue.repository.firestore;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ClosureRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** closures/{id}: active LGU-reported closures used by the risk engine (rule 1). */
public final class FirestoreClosureRepository implements ClosureRepository {

    private static final String COLLECTION = "closures";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreClosureRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public String newId() {
        return db.collection(COLLECTION).document().getId();
    }

    private Query activeQuery() {
        return db.collection(COLLECTION).whereEqualTo("active", true);
    }

    @Override
    public ListenerHandle listenActive(Callback<List<RoadClosure>> callback) {
        ListenerRegistration registration = activeQuery().addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot != null) callback.onSuccess(toList(snapshot));
        });
        return registration::remove;
    }

    @Override
    public List<RoadClosure> loadActiveBlocking() {
        try {
            return toList(Tasks.await(activeQuery().get(), 6, TimeUnit.SECONDS));
        } catch (Exception serverProblem) {
            try {
                return toList(Tasks.await(activeQuery().get(Source.CACHE), 3, TimeUnit.SECONDS));
            } catch (Exception noCache) {
                return new ArrayList<>();
            }
        }
    }

    private static List<RoadClosure> toList(QuerySnapshot snapshot) {
        List<RoadClosure> closures = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            try {
                RoadClosure closure = FirestoreMappers.closure(doc);
                if (!closure.isArchived()) closures.add(closure);
            } catch (ValidationException | RuntimeException ignored) {
                // skip malformed entries
            }
        }
        return closures;
    }

    @Override
    public void save(RoadClosure closure, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(closure.getId()), FirestoreMappers.closure(closure, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
