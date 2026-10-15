package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.FirebaseFirestore;
import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.FamilyRepository;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.service.FreshnessService;

/** families/{ownerUid}: one record per resident, readable and writable only by its owner. */
public final class FirestoreFamilyRepository implements FamilyRepository {

    private final FirebaseFirestore db;
    private final FreshnessService freshness;
    private final ConnectivityMonitor connectivity;

    public FirestoreFamilyRepository(FirebaseFirestore db, FreshnessService freshness, ConnectivityMonitor connectivity) {
        this.db = db;
        this.freshness = freshness;
        this.connectivity = connectivity;
    }

    @Override
    public void load(String ownerUid, Callback<SourcedData<FamilyRecord>> callback) {
        db.collection("families").document(ownerUid).get()
                .addOnSuccessListener(doc -> {
                    boolean fromCache = doc.getMetadata().isFromCache();
                    if (!doc.exists()) {
                        callback.onSuccess(freshness.wrap(null, fromCache, FreshnessService.FAMILY, "Your family record"));
                        return;
                    }
                    try {
                        callback.onSuccess(freshness.wrap(FirestoreMappers.family(doc), fromCache,
                                FreshnessService.FAMILY, "Your family record"));
                    } catch (InvalidFamilyRecordException damaged) {
                        callback.onError(damaged);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void save(FamilyRecord record, Callback<Void> callback) {
        if (record.getOwnerUid() == null) {
            callback.onError(new IllegalStateException("The record has no owner."));
            return;
        }
        FirestoreWrites.complete(db.collection("families").document(record.getOwnerUid())
                .set(FirestoreMappers.family(record)), connectivity, callback);
    }
}
