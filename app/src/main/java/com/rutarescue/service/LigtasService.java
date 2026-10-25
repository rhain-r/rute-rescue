package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.LigtasRepository;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.ProofStorage;

import java.util.List;

/**
 * Ligtas Priority use cases (DOCX sections 4, 5, 12): residents submit with a synthetic
 * proof image; a moderator of the same city approves, asks for documents or rejects with a
 * message (and may change it later); every decision is stored together with an audit event;
 * approved applications get a QR that contains only an opaque application id.
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

    /** All applications of the staff member's city (moderators decide; the LGU sees the numbers). */
    public ListenerHandle listenForReviewer(StaffProfile staff, Callback<SourcedData<List<LigtasApplication>>> callback) {
        return repository.listenForCity(staff.getJurisdiction(), callback);
    }

    public ListenerHandle listenOne(String id, Callback<SourcedData<LigtasApplication>> callback) {
        return repository.listenOne(id, callback);
    }

    public void downloadProof(String proofRef, Callback<byte[]> callback) {
        proofStorage.download(proofRef, callback);
    }

    /** The member id used when residents apply for themselves (2026-10-25). */
    public static final String SELF_MEMBER_ID = "self";

    /** A family member applies: uploads the synthetic proof first, then creates the PENDING application. */
    public void submit(UserProfile resident, FamilyRecord family, FamilyMember member, NeedCategory category,
                       String justification, byte[] demoProofJpeg, Callback<LigtasApplication> callback) {
        if (member == null) {
            callback.onError(new ValidationException("member", "Choose who needs priority help."));
            return;
        }
        submitFor(resident, family, member.getMemberId(), member.getFullName(), category, justification,
                demoProofJpeg, callback);
    }

    /**
     * The resident applies for themselves (2026-10-25), with or without a family record: a pregnant
     * or senior resident often lives alone or has not added a record yet.
     */
    public void submitForSelf(UserProfile resident, FamilyRecord family, NeedCategory category, String justification,
                              byte[] demoProofJpeg, Callback<LigtasApplication> callback) {
        submitFor(resident, family, SELF_MEMBER_ID, resident.getDisplayName(), category, justification,
                demoProofJpeg, callback);
    }

    /** The family record's city, or the resident's own city when there is no record yet. */
    private void submitFor(UserProfile resident, FamilyRecord family, String memberId, String memberName,
                           NeedCategory category, String justification, byte[] demoProofJpeg,
                           Callback<LigtasApplication> callback) {
        MetroManilaCity city = family != null && family.getCity() != null ? family.getCity() : resident.getCity();
        if (city == null) {
            callback.onError(new ValidationException("family", "Add your city (My Record) before applying."));
            return;
        }
        if (demoProofJpeg == null || demoProofJpeg.length == 0) {
            callback.onError(new ValidationException("proof", "Attach a synthetic/demo proof image."));
            return;
        }
        String id = repository.newId();
        // Validate the text fields before uploading anything.
        try {
            LigtasApplication.submit(id, resident.getUid(), city, resident.getDisplayName(), memberId, memberName,
                    category, justification, "pending-upload", resident.getEmail(), System.currentTimeMillis());
        } catch (ValidationException invalid) {
            callback.onError(invalid);
            return;
        }
        proofStorage.upload(resident.getUid(), id, city.code(), demoProofJpeg, new Callback<String>() {
            @Override
            public void onSuccess(String proofRef) {
                try {
                    LigtasApplication application = LigtasApplication.submit(id, resident.getUid(), city,
                            resident.getDisplayName(), memberId, memberName, category, justification, proofRef,
                            resident.getEmail(), System.currentTimeMillis());
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

    /**
     * Moderator: approve, ask for documents or reject - also later, to correct a decision. The
     * model enforces the moderator's city and a 5-300 character message for the resident.
     */
    public void decide(StaffProfile moderator, LigtasApplication application, ApplicationStatus decision, String message,
                       Callback<Void> callback) {
        long now = System.currentTimeMillis();
        ApplicationStatus before = application.getStatus();
        try {
            application.decide(moderator, decision, message, now);
        } catch (ValidationException | IllegalStateException | IllegalArgumentException e) {
            callback.onError(e);
            return;
        }
        String action = decision == ApplicationStatus.APPROVED ? "APPROVE"
                : decision == ApplicationStatus.REJECTED ? "REJECT" : "ASK_DOCUMENTS";
        String reason = (before.isDecision() ? "Changed from " + before.getLabel() + ": " : "")
                + application.getReviewReason();
        AuditEvent audit = AuditEvent.create(moderator.getUid(), moderator.getJurisdiction().code(), action,
                application, reason, now);
        repository.saveDecision(application, audit, callback);
    }

    /** Resident: sends a new synthetic proof after "Needs documents"; it goes back to Pending. */
    public void resubmit(UserProfile resident, LigtasApplication application, byte[] demoProofJpeg,
                         Callback<Void> callback) {
        if (demoProofJpeg == null || demoProofJpeg.length == 0) {
            callback.onError(new ValidationException("proof", "Attach a synthetic/demo proof image."));
            return;
        }
        if (application.getCity() == null) {
            callback.onError(new IllegalStateException("This application has no city."));
            return;
        }
        // A new proof document next to the first one (proofs are never overwritten).
        String proofId = application.getId() + "-" + application.getHistory().size();
        proofStorage.upload(resident.getUid(), proofId, application.getCity().code(), demoProofJpeg, new Callback<String>() {
            @Override
            public void onSuccess(String proofRef) {
                try {
                    application.resubmit(resident.getUid(), proofRef, System.currentTimeMillis());
                } catch (IllegalStateException | IllegalArgumentException e) {
                    callback.onError(e);
                    return;
                }
                repository.saveResubmission(application, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }
}
