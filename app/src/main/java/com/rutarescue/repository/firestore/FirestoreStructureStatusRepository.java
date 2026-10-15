package com.rutarescue.repository.firestore;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** structureStatus/{osmWayId}: LGU passability evidence for bridges/tunnels. */
public final class FirestoreStructureStatusRepository implements StructureStatusRepository {

    private static final String COLLECTION = "structureStatus";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreStructureStatusRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public ListenerHandle listen(Callback<Map<String, StatusRecord>> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snapshot != null) callback.onSuccess(toMap(snapshot));
        });
        return registration::remove;
    }

    @Override
    public Map<String, StatusRecord> loadBlocking() {
        try {
            return toMap(Tasks.await(db.collection(COLLECTION).get(), 6, TimeUnit.SECONDS));
        } catch (Exception serverProblem) {
            try {
                return toMap(Tasks.await(db.collection(COLLECTION).get(Source.CACHE), 3, TimeUnit.SECONDS));
            } catch (Exception noCache) {
                return new HashMap<>();
            }
        }
    }

    private static Map<String, StatusRecord> toMap(QuerySnapshot snapshot) {
        Map<String, StatusRecord> map = new HashMap<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            map.put(doc.getId(), new StatusRecord(StructureStatus.fromName(FirestoreMappers.str(doc, "status")),
                    FirestoreMappers.str(doc, "evidence"), FirestoreMappers.num(doc, "updatedAt"),
                    FirestoreMappers.str(doc, "updatedBy")));
        }
        return map;
    }

    @Override
    public void setStatus(String structureId, StatusRecord record, MetroManilaCity city, AuditEvent audit,
                          Callback<Void> callback) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", record.status.name());
        map.put("evidence", record.evidence);
        map.put("updatedAt", record.updatedAt);
        map.put("updatedBy", record.updatedBy);
        map.put("jurisdiction", city.code());
        map.put("lastAuditId", audit.getId());
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(structureId), map);
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
