package com.rutarescue.repository.firestore;

import com.google.android.gms.tasks.Task;
import com.rutarescue.repository.Callback;
import com.rutarescue.service.ConnectivityMonitor;
import com.rutarescue.util.SafeLog;

/**
 * Offline-friendly write completion. Firestore applies a write to the local cache at
 * once, but its Task only completes when the server confirms it. While offline we report
 * success immediately ("saved on this phone, will sync"), so the app never blocks.
 */
final class FirestoreWrites {

    private static final String TAG = "RR-Writes";

    private FirestoreWrites() {
    }

    static void complete(Task<Void> task, ConnectivityMonitor connectivity, Callback<Void> callback) {
        if (connectivity.isOnline()) {
            task.addOnSuccessListener(ignored -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onError);
        } else {
            callback.onSuccess(null);
            task.addOnFailureListener(error -> SafeLog.w(TAG, "A queued write was rejected when it synced", error));
        }
    }
}
