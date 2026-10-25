package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.priority.CheckResult;
import com.rutarescue.model.priority.PriorityCard;
import com.rutarescue.model.priority.PriorityGroup;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.PriorityCardRepository;

import java.util.Map;

/**
 * Priority IDs (2026-10-22): issued to residents in a priority group, scanned and checked by
 * moderators at the evacuation center. Every check is saved with an audit event.
 */
public final class PriorityIdService {

    private final PriorityCardRepository repository;

    public PriorityIdService(PriorityCardRepository repository) {
        this.repository = repository;
    }

    /** Issues a new ID for the signed-in resident (registration, or later from Settings). */
    public void issue(ResidentProfile resident, PriorityGroup group, Callback<PriorityCard> callback) {
        PriorityCard card;
        try {
            card = PriorityCard.issue(PriorityCard.newCode(), resident, group, System.currentTimeMillis());
        } catch (ValidationException e) {
            callback.onError(e);
            return;
        }
        repository.create(card, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                callback.onSuccess(card);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /** The resident's own ID (null when they have none). */
    public ListenerHandle listenMine(String ownerUid, Callback<PriorityCard> callback) {
        return repository.listenMine(ownerUid, callback);
    }

    /** Opens a scanned or typed ID. */
    public void open(String scannedOrTyped, Callback<PriorityCard> callback) {
        String code = PriorityCard.parseQrPayload(scannedOrTyped);
        if (code == null) {
            callback.onError(new ValidationException("code", "This is not a RutaRescue priority ID."));
            return;
        }
        repository.get(code, callback);
    }

    /** Moderator: saves the checklist result (OK / Missing for every item) and a note for the resident. */
    public void recordCheck(StaffProfile moderator, PriorityCard card, Map<String, CheckResult> results, String note,
                            Callback<Void> callback) {
        long now = System.currentTimeMillis();
        String center = moderator instanceof ModeratorProfile ? ((ModeratorProfile) moderator).getCenterName() : "";
        try {
            card.recordCheck(moderator, results, note, center, now);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        int missing = card.getMissingItems().size();
        // No name in the audit text: the record id is enough to find the card.
        AuditEvent audit = AuditEvent.create(moderator.getUid(), moderator.getJurisdiction().code(), "CHECK_PRIORITY_ID",
                card, card.getGroup().getLabel() + " ID checked: " + (missing == 0 ? "complete" : missing + " missing"), now);
        repository.saveCheck(card, audit, callback);
    }
}
