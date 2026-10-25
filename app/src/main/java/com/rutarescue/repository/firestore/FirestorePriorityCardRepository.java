package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.PriorityCardRepository;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.HashMap;
import java.util.Map;

/**
 * priorityIds/{code} (2026-10-22). The resident finds their own card with a query on ownerUid
 * (the rules allow listing only one's own); a moderator opens a card by its code only.
 */
public final class FirestorePriorityCardRepository implements PriorityCardRepository {

    private static final String COLLECTION = "priorityIds";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestorePriorityCardRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public void create(PriorityCard card, Callback<Void> callback) {
        FirestoreWrites.complete(db.collection(COLLECTION).document(card.getId())
                .set(FirestoreMappers.priorityCard(card, null)), connectivity, callback);
    }

    @Override
    public ListenerHandle listenMine(String ownerUid, Callback<PriorityCard> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).whereEqualTo("ownerUid", ownerUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshot == null) return;
                    PriorityCard newest = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try {
                            PriorityCard card = FirestoreMappers.priorityCard(doc);
                            if (newest == null || card.getIssuedAt() > newest.getIssuedAt()) newest = card;
                        } catch (RuntimeException damaged) {
                            // skip a damaged card
                        }
                    }
                    callback.onSuccess(newest);
                });
        return registration::remove;
    }

    @Override
    public void get(String code, Callback<PriorityCard> callback) {
        db.collection(COLLECTION).document(code).get()
                .addOnSuccessListener(doc -> {
                    try {
                        if (!doc.exists()) throw new IllegalStateException("No priority ID has the code " + code + ".");
                        callback.onSuccess(FirestoreMappers.priorityCard(doc));
                    } catch (RuntimeException e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** Only the check fields change; the rules check that nothing else does. */
    @Override
    public void saveCheck(PriorityCard card, AuditEvent audit, Callback<Void> callback) {
        Map<String, Object> all = FirestoreMappers.priorityCard(card, audit.getId());
        Map<String, Object> changes = new HashMap<>();
        for (String key : new String[]{"checks", "missingCount", "flagNote", "checkedBy", "checkedByName", "checkedAt",
                "checkedAtCenter", "lastAuditId"}) {
            changes.put(key, all.get(key));
        }
        WriteBatch batch = db.batch();
        batch.update(db.collection(COLLECTION).document(card.getId()), changes);
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
