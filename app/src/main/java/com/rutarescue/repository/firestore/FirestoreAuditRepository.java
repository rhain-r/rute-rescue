package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.repository.AuditRepository;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;

import java.util.ArrayList;
import java.util.List;

/** auditEvents/{id}: create-only trail, readable by LGU staff of the same jurisdiction. */
public final class FirestoreAuditRepository implements AuditRepository {

    private final FirebaseFirestore db;

    public FirestoreAuditRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public ListenerHandle listenForCity(MetroManilaCity city, Callback<List<AuditEvent>> callback) {
        ListenerRegistration registration = db.collection("auditEvents")
                .whereEqualTo("jurisdiction", city.code())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null) return;
                    List<AuditEvent> events = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try {
                            events.add(FirestoreMappers.auditEvent(doc));
                        } catch (RuntimeException ignored) {
                            // skip malformed entries
                        }
                    }
                    events.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                    callback.onSuccess(events);
                });
        return registration::remove;
    }
}
