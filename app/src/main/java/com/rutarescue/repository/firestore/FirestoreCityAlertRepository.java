package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rutarescue.model.advisory.CityAlert;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.CityAlertRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.service.ConnectivityMonitor;

/**
 * cityAlerts/{CITY CODE} (2026-10-22): one document per city. Everyone signed in reads it; the
 * rules let only that city's LGU write it, together with an audit event in the same batch.
 */
public final class FirestoreCityAlertRepository implements CityAlertRepository {

    private static final String COLLECTION = "cityAlerts";

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreCityAlertRepository(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public ListenerHandle listen(MetroManilaCity city, Callback<CityAlert> callback) {
        ListenerRegistration registration = db.collection(COLLECTION).document(city.code())
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        // A database still on the older rules cannot read it: show the normal level.
                        callback.onSuccess(CityAlert.none(city));
                        return;
                    }
                    if (doc == null) return;
                    try {
                        callback.onSuccess(FirestoreMappers.cityAlert(doc, city));
                    } catch (RuntimeException damaged) {
                        callback.onSuccess(CityAlert.none(city));
                    }
                });
        return registration::remove;
    }

    @Override
    public void save(CityAlert alert, AuditEvent audit, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION).document(alert.getCity().code()), FirestoreMappers.cityAlert(alert, audit.getId()));
        batch.set(db.collection("auditEvents").document(audit.getId()), FirestoreMappers.audit(audit));
        FirestoreWrites.complete(batch.commit(), connectivity, callback);
    }
}
