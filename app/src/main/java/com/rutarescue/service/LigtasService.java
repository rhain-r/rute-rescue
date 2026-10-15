package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.LigtasRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.ProofStorage;

import java.util.List;

/**
 * Ligtas Priority use cases (DOCX sections 4, 5, 12): residents submit with a synthetic
 * proof image; LGU staff of the same city approve or reject with a reason; every decision
 * is stored together with an audit event; approved applications get a QR that contains
 * only an opaque application id.
 */
public final class LigtasService {

    private final LigtasRepository repository;
    private final ProofStorage proofStorage;

    public LigtasService(LigtasRepository repository, ProofStorage proofStorage) {
        this.repository = repository;
        this.proofStorage = proofStorage;
    }

    public ListenerHandle listenMine(String uid, Callback<SourcedData<List<LigtasApplication>>> callback) {
        return repository.listenMine(uid, callback);
    }

    public ListenerHandle listenForReviewer(LguProfile lgu, Callback<SourcedData<List<LigtasApplication>>> callback) {
        return repository.listenForCity(lgu.getJurisdiction(), callback);
    }

    public ListenerHandle listenOne(String id, Callback<SourcedData<LigtasApplication>> callback) {
        return repository.listenOne(id, callback);
    }

    public void downloadProof(String proofRef, Callback<byte[]> callback) {
        proofStorage.download(proofRef, callback);
    }

    /** Uploads the synthetic proof first, then creates the PENDING application. */
    public void submit(UserProfile resident, FamilyRecord family, FamilyMember member, NeedCategory category,
                       String justification, byte[] demoProofJpeg, Callback<LigtasApplication> callback) {
        if (family == null || family.getCity() == null) {
            callback.onError(new ValidationException("family", "Create your family record (with your city) before applying."));
            return;
        }
        if (member == null) {
            callback.onError(new ValidationException("member", "Choose the family member who needs priority help."));
            return;
        }
        if (demoProofJpeg == null || demoProofJpeg.length == 0) {
            callback.onError(new ValidationException("proof", "Attach a synthetic/demo proof image."));
            return;
        }
        String id = repository.newId();
        // Validate the text fields before uploading anything.
        try {
            LigtasApplication.submit(id, resident.getUid(), family.getCity(), resident.getDisplayName(),
                    member.getMemberId(), member.getFullName(), category, justification, "pending-upload",
                    System.currentTimeMillis());
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        proofStorage.upload(resident.getUid(), id, family.getCity().code(), demoProofJpeg, new Callback<String>() {
            @Override
            public void onSuccess(String proofRef) {
                try {
                    LigtasApplication application = LigtasApplication.submit(id, resident.getUid(), family.getCity(),
                            resident.getDisplayName(), member.getMemberId(), member.getFullName(), category,
                            justification, proofRef, System.currentTimeMillis());
                    repository.submit(application, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            callback.onSuccess(application);
                        }

                        @Override
                        public void onError(Exception error) {
                            callback.onError(error);
                        }
                    });
                } catch (ValidationException invalid) {
                    callback.onError(invalid);
                }
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /** Approve or reject; the model enforces jurisdiction, PENDING status and a reason. */
    public void decide(LguProfile reviewer, LigtasApplication application, boolean approve, String reason,
                       Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            if (approve) {
                application.approve(reviewer, reason, now);
            } else {
                application.reject(reviewer, reason, now);
            }
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        AuditEvent audit = AuditEvent.create(reviewer.getUid(), reviewer.getJurisdiction().code(),
                approve ? "APPROVE" : "REJECT", application, application.getReviewReason(), now);
        repository.saveDecision(application, audit, callback);
    }
}
