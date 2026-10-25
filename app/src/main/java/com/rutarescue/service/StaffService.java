package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.model.user.StaffRequest;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.repository.StaffRepository;

import java.util.List;

/**
 * Staff sign-up decisions (2026-10-20): the LGU of a city approves or rejects the people who
 * asked to become moderators or LGU officers there, and (2026-10-22) assigns each moderator the
 * evacuation center they run. Every decision has an audit event; the
 * model checks the LGU's role and city, and the Firestore rules check them again.
 */
public final class StaffService {

    private final StaffRepository repository;

    public StaffService(StaffRepository repository) {
        this.repository = repository;
    }

    public ListenerHandle listenRequests(StaffProfile lgu, Callback<List<StaffRequest>> callback) {
        return repository.listenRequests(lgu.getJurisdiction(), callback);
    }

    /** The approved staff of the LGU's city (to see and change which center each moderator runs). */
    public ListenerHandle listenStaff(StaffProfile lgu, Callback<List<StaffProfile>> callback) {
        return repository.listenStaff(lgu.getJurisdiction(), callback);
    }

    /**
     * Approves a sign-up. A moderator is given the center they will run (center must be one of the
     * city's centers); for an LGU sign-up pass null.
     */
    public void approve(StaffProfile lgu, StaffRequest request, EvacuationCenter center, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        StaffProfile newStaff;
        try {
            if (center != null && center.getCity() != lgu.getJurisdiction()) {
                throw new IllegalStateException("Choose a center in " + lgu.getJurisdiction().getDisplayName() + ".");
            }
            request.approve(lgu, center == null ? "" : center.getId(), center == null ? "" : center.getName(), now);
            newStaff = request.toProfile();
        } catch (IllegalStateException e) {
            callback.onError(e);
            return;
        }
        AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "APPROVE", request,
                "Approved " + request.getDisplayName() + " as " + request.getRole().getLabel(), now);
        repository.approve(request, newStaff, audit, callback);
    }

    /** LGU: gives one of its moderators another center to run (or the first one, for older accounts). */
    public void assignCenter(StaffProfile lgu, ModeratorProfile moderator, EvacuationCenter center, Callback<Void> callback) {
        if (!lgu.canApproveStaff() || moderator.getJurisdiction() != lgu.getJurisdiction()
                || center == null || center.getCity() != lgu.getJurisdiction()) {
            callback.onError(new IllegalStateException("The " + moderator.getJurisdiction().getDisplayName()
                    + " LGU assigns its moderators to its own centers."));
            return;
        }
        long now = System.currentTimeMillis();
        ModeratorProfile assigned = new ModeratorProfile(moderator.getUid(), moderator.getDisplayName(), moderator.getEmail(),
                moderator.getJurisdiction(), moderator.getPosition(), center.getId(), center.getName());
        AuditEvent audit = new AuditEvent(java.util.UUID.randomUUID().toString(), lgu.getUid(),
                lgu.getJurisdiction().code(), "ASSIGN_CENTER", "staff", moderator.getUid(),
                moderator.getDisplayName() + " now runs " + center.getName(), now);
        repository.assignCenter(assigned, audit, callback);
    }

    public void reject(StaffProfile lgu, StaffRequest request, String reason, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        try {
            request.reject(lgu, reason, now);
        } catch (ValidationException | IllegalStateException e) {
            callback.onError(e);
            return;
        }
        AuditEvent audit = AuditEvent.create(lgu.getUid(), lgu.getJurisdiction().code(), "REJECT", request,
                "Did not approve " + request.getDisplayName() + ": " + request.getDecisionReason(), now);
        repository.reject(request, audit, callback);
    }
}
