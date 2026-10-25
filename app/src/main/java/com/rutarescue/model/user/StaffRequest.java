package com.rutarescue.model.user;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.validation.AccountValidator;
import com.rutarescue.validation.NameFormatter;
import com.rutarescue.validation.TextRules;

/**
 * A staff sign-up (2026-10-20): someone asks to become a moderator or LGU officer of one city.
 * The account stays locked until that city's LGU approves it, so nobody can make themselves
 * staff. The document id is the person's Firebase Auth uid (staffRequests/{uid}).
 *
 * Encapsulation: the status only moves PENDING -> APPROVED or PENDING -> REJECTED, through
 * approve()/reject(), and only by an LGU officer of the same city.
 */
public class StaffRequest extends AuditableEntity {

    private final String displayName;
    private final String email;
    private final Role role;
    private final MetroManilaCity jurisdiction;
    private final String position;
    private final long requestedAt;
    private StaffRequestStatus status;
    private String decidedBy;
    private String decisionReason;
    private long decidedAt;
    /** 2026-10-22: the center the LGU gives a new moderator when approving (not stored on the request). */
    private String assignedCenterId = "";
    private String assignedCenterName = "";

    /** Used by the repository when it reads a stored request. */
    public StaffRequest(String uid, String displayName, String email, Role role, MetroManilaCity jurisdiction,
                        String position, long requestedAt, StaffRequestStatus status, String decidedBy,
                        String decisionReason, long decidedAt) {
        super(uid, null, false);
        this.displayName = displayName == null ? "" : displayName;
        this.email = email == null ? "" : email;
        this.role = role == Role.MODERATOR ? Role.MODERATOR : Role.LGU;
        this.jurisdiction = jurisdiction;
        this.position = position == null ? "" : position;
        this.requestedAt = requestedAt;
        this.status = status == null ? StaffRequestStatus.PENDING : status;
        this.decidedBy = decidedBy == null ? "" : decidedBy;
        this.decisionReason = decisionReason == null ? "" : decisionReason;
        this.decidedAt = decidedAt;
    }

    /** Checks the sign-up form and creates a PENDING request. */
    public static StaffRequest create(String uid, String fullName, String email, Role role, MetroManilaCity city,
                                      String position, long now) throws ValidationException {
        String name = NameFormatter.requirePersonName("fullName", fullName);
        String checkedEmail = AccountValidator.requireEmail(email);
        if (role != Role.MODERATOR && role != Role.LGU) {
            throw new ValidationException("role", "Choose Moderator or LGU.");
        }
        if (city == null) {
            throw new ValidationException("city", "Choose the city you work for.");
        }
        String checkedPosition = TextRules.optionalText("position", position);
        return new StaffRequest(uid, name, checkedEmail, role, city, checkedPosition, now,
                StaffRequestStatus.PENDING, "", "", 0L);
    }

    @Override
    public String getEntityType() {
        return "staffRequest";
    }

    /** Approves an LGU sign-up (a moderator also needs a center: see the other approve). */
    public void approve(StaffProfile lgu, long now) {
        approve(lgu, "", "", now);
    }

    /**
     * Approves the sign-up. A moderator runs one evacuation center, so the LGU must choose it here
     * (one of the city's centers); for an LGU sign-up the center is ignored.
     */
    public void approve(StaffProfile lgu, String centerId, String centerName, long now) {
        checkDecider(lgu);
        if (role == Role.MODERATOR && (centerId == null || centerId.isBlank())) {
            throw new IllegalStateException("Choose the evacuation center this moderator will run.");
        }
        assignedCenterId = role == Role.MODERATOR ? centerId.strip() : "";
        assignedCenterName = role == Role.MODERATOR && centerName != null ? centerName.strip() : "";
        status = StaffRequestStatus.APPROVED;
        decidedBy = lgu.getUid();
        decisionReason = "Approved by " + lgu.getDisplayName();
        decidedAt = now;
        markUpdated(lgu.getUid(), now);
    }

    public void reject(StaffProfile lgu, String reason, long now) throws ValidationException {
        checkDecider(lgu);
        String checkedReason = TextRules.multilineText("reason", reason, 5, 300);
        status = StaffRequestStatus.REJECTED;
        decidedBy = lgu.getUid();
        decisionReason = checkedReason;
        decidedAt = now;
        markUpdated(lgu.getUid(), now);
    }

    private void checkDecider(StaffProfile lgu) {
        if (status != StaffRequestStatus.PENDING) {
            throw new IllegalStateException("This request was already " + status.getLabel().toLowerCase() + ".");
        }
        if (lgu == null || !lgu.canApproveStaff() || lgu.getJurisdiction() != jurisdiction) {
            throw new IllegalStateException("Only the " + cityName() + " LGU can decide this request.");
        }
    }

    /** The staff profile this request becomes once approved (polymorphism: Moderator or LGU). */
    public StaffProfile toProfile() {
        if (status != StaffRequestStatus.APPROVED) {
            throw new IllegalStateException("The request is not approved yet.");
        }
        return role == Role.MODERATOR
                ? new ModeratorProfile(getId(), displayName, email, jurisdiction, position, assignedCenterId, assignedCenterName)
                : new LguProfile(getId(), displayName, email, jurisdiction, position);
    }

    /** What the person sees when they sign in before (or after a rejected) approval. */
    public String waitingMessage() {
        if (status == StaffRequestStatus.REJECTED) {
            return "Your " + role.getLabel() + " request for " + cityName() + " was not approved: " + decisionReason;
        }
        return "Your " + role.getLabel() + " account for " + cityName() + " is waiting for approval by the "
                + cityName() + " LGU. You can sign in once they approve it.";
    }

    private String cityName() {
        return jurisdiction == null ? "your city" : jurisdiction.getDisplayName();
    }

    public String getUid() { return getId(); }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public MetroManilaCity getJurisdiction() { return jurisdiction; }
    public String getPosition() { return position; }
    public long getRequestedAt() { return requestedAt; }
    public StaffRequestStatus getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public long getDecidedAt() { return decidedAt; }
}
