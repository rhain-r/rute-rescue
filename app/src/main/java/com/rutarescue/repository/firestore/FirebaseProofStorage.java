package com.rutarescue.repository.firestore;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ProofStorage;

/**
 * Stores synthetic proof images in Firebase Storage at ligtas/{uid}/{applicationId}/proof.jpg.
 * storage.rules restrict uploads to the owner, JPEG/PNG only, and at most 2 MB.
 * Requires the Firebase Blaze plan (set USE_FIREBASE_STORAGE=true in local.properties).
 */
public final class FirebaseProofStorage implements ProofStorage {

    private static final String PREFIX = "storage:";
    private static final long MAX_DOWNLOAD_BYTES = 3L * 1024 * 1024;

    private final FirebaseStorage storage;

    public FirebaseProofStorage(FirebaseStorage storage) {
        this.storage = storage;
    }

    @Override
    public void upload(String ownerUid, String applicationId, String city, byte[] jpeg, Callback<String> callback) {
        String path = "ligtas/" + ownerUid + "/" + applicationId + "/proof.jpg";
        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
        storage.getReference(path).putBytes(jpeg, metadata)
                .addOnSuccessListener(ignored -> callback.onSuccess(PREFIX + path))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void download(String proofRef, Callback<byte[]> callback) {
        if (proofRef == null || !proofRef.startsWith(PREFIX)) {
            callback.onError(new IllegalArgumentException("Not a Firebase Storage proof reference."));
            return;
        }
        StorageReference reference = storage.getReference(proofRef.substring(PREFIX.length()));
        reference.getBytes(MAX_DOWNLOAD_BYTES)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }
}
