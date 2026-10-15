package com.rutarescue.repository.firestore;

import com.google.firebase.firestore.FirebaseFirestore;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ProofStorage;
import com.rutarescue.service.ConnectivityMonitor;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Free-plan fallback: keeps the compressed synthetic proof (max ~300 KB) inside a Firestore
 * document ligtasProofs/{applicationId}. Same access rules as the application itself.
 */
public final class FirestoreInlineProofStorage implements ProofStorage {

    private static final String PREFIX = "inline:";
    public static final int MAX_BYTES = 300 * 1024;

    private final FirebaseFirestore db;
    private final ConnectivityMonitor connectivity;

    public FirestoreInlineProofStorage(FirebaseFirestore db, ConnectivityMonitor connectivity) {
        this.db = db;
        this.connectivity = connectivity;
    }

    @Override
    public void upload(String ownerUid, String applicationId, String city, byte[] jpeg, Callback<String> callback) {
        if (jpeg.length > MAX_BYTES) {
            callback.onError(new IllegalArgumentException("The demo image is too large after compression (max 300 KB)."));
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUid", ownerUid);
        map.put("city", city);
        map.put("contentType", "image/jpeg");
        map.put("imageBase64", Base64.getEncoder().encodeToString(jpeg));
        map.put("createdAt", System.currentTimeMillis());
        FirestoreWrites.complete(db.collection("ligtasProofs").document(applicationId).set(map), connectivity,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        callback.onSuccess(PREFIX + applicationId);
                    }

                    @Override
                    public void onError(Exception error) {
                        callback.onError(error);
                    }
                });
    }

    @Override
    public void download(String proofRef, Callback<byte[]> callback) {
        if (proofRef == null || !proofRef.startsWith(PREFIX)) {
            callback.onError(new IllegalArgumentException("Not an inline proof reference."));
            return;
        }
        db.collection("ligtasProofs").document(proofRef.substring(PREFIX.length())).get()
                .addOnSuccessListener(doc -> {
                    String data = doc.getString("imageBase64");
                    if (data == null) {
                        callback.onError(new IllegalStateException("Proof image not found."));
                    } else {
                        callback.onSuccess(Base64.getDecoder().decode(data));
                    }
                })
                .addOnFailureListener(callback::onError);
    }
}
