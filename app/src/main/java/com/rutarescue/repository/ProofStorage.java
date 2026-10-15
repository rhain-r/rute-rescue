package com.rutarescue.repository;

/**
 * Where synthetic/demo Ligtas proof images are kept.
 *
 * Polymorphism: FirebaseProofStorage uses Firebase Storage (needs the Blaze plan);
 * FirestoreInlineProofStorage keeps a small compressed copy inside Firestore so the demo
 * also works on the free plan. The choice is made once in AppContainer.
 */
public interface ProofStorage {

    /** Uploads a JPEG and returns a reference string to store in the application. */
    void upload(String ownerUid, String applicationId, String city, byte[] jpeg, Callback<String> callback);

    void download(String proofRef, Callback<byte[]> callback);
}
