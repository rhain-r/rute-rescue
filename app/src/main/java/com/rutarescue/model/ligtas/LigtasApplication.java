package com.rutarescue.model.ligtas;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.validation.TextRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Ligtas Priority application (DOCX sections 4, 5 and 12).
 *
 * Encapsulation: the status can only move PENDING -> APPROVED or PENDING -> REJECTED
 * through approve()/reject(), which require an authorized reviewer and a reason, and
 * always append to the history. Proof images must be synthetic demo images only.
 */
public class LigtasApplication extends AuditableEntity {

    /** QR payload prefix. The QR contains ONLY this prefix and the opaque document id. */
    public static final String QR_PREFIX = "RUTARESCUE:LIGTAS:";
    private static final Pattern QR_PATTERN = Pattern.compile("RUTARESCUE:LIGTAS:([A-Za-z0-9_-]{8,64})");

    private final String ownerUid;
    private final MetroManilaCity city;
    private final String applicantName;
    private final String memberId;
    private final String memberName;
    private final NeedCategory category;
    private final String justification;
    private final String proofRef;
    private final long submittedAt;
    private ApplicationStatus status;
    private String reviewerUid;
    private String reviewReason;
    private long decidedAt;
    private final List<StatusChange> history = new ArrayList<>();

    /** Full constructor used by repositories when loading a saved application. */
    public LigtasApplication(String id, String ownerUid, MetroManilaCity city, String applicantName,
                             String memberId, String memberName, NeedCategory category, String justification,
                             String proofRef, long submittedAt, ApplicationStatus status, String reviewerUid,
                             String reviewReason, long decidedAt, List<StatusChange> history) {
        super(id, null, false);
        this.ownerUid = ownerUid;
        this.city = city;
        this.applicantName = applicantName == null ? "" : applicantName;
        this.memberId = memberId == null ? "" : memberId;
        this.memberName = memberName == null ? "" : memberName;
        this.category = category == null ? NeedCategory.OTHER : category;
        this.justification = justification == null ? "" : justification;
        this.proofRef = proofRef == null ? "" : proofRef;
        this.submittedAt = submittedAt;
        this.status = status == null ? ApplicationStatus.PENDING : status;
        this.reviewerUid = reviewerUid == null ? "" : reviewerUid;
        this.reviewReason = reviewReason == null ? "" : reviewReason;
        this.decidedAt = decidedAt;
        if (history != null) this.history.addAll(history);
    }

    /** Creates a new PENDING application after validating the resident's input. */
    public static LigtasApplication submit(String id, String ownerUid, MetroManilaCity city, String applicantName,
                                           String memberId, String memberName, NeedCategory category,
                                           String justification, String proofRef, long now)
            throws ValidationException {
        if (ownerUid == null || ownerUid.isBlank()) throw new ValidationException("owner", "Sign in before applying.");
        if (city == null) throw new ValidationException("city", "Your record needs a city before applying.");
        if (category == null) throw new ValidationException("category", "Choose a need category.");
        String checkedMember = TextRules.requiredText("memberName", memberName);
        String checkedJustification = TextRules.multilineText("justification", justification, 10, 500);
        if (proofRef == null || proofRef.isBlank()) {
            throw new ValidationException("proof", "Attach a synthetic/demo proof image.");
        }
        List<StatusChange> history = new ArrayList<>();
        history.add(new StatusChange(ApplicationStatus.PENDING, ownerUid, "Submitted by resident", now));
        return new LigtasApplication(id, ownerUid, city, applicantName, memberId, checkedMember, category,
                checkedJustification, proofRef, now, ApplicationStatus.PENDING, "", "", 0L, history);
    }

    @Override
    public String getEntityType() {
        return "ligtasApplication";
    }

    public void approve(UserProfile reviewer, String reason, long now) throws ValidationException {
        decide(reviewer, ApplicationStatus.APPROVED, reason, now);
    }

    public void reject(UserProfile reviewer, String reason, long now) throws ValidationException {
        decide(reviewer, ApplicationStatus.REJECTED, reason, now);
    }

    private void decide(UserProfile reviewer, ApplicationStatus decision, String reason, long now)
            throws ValidationException {
        if (status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("This application was already " + status.getLabel().toLowerCase() + ".");
        }
        if (reviewer == null || !reviewer.canReview(this)) {
            throw new IllegalStateException("Only LGU personnel of " + cityName() + " can review this application.");
        }
        String checkedReason = TextRules.multilineText("reason", reason, 5, 300);
        status = decision;
        reviewerUid = reviewer.getUid();
        reviewReason = checkedReason;
        decidedAt = now;
        history.add(new StatusChange(decision, reviewer.getUid(), checkedReason, now));
        markUpdated(reviewer.getUid(), now);
    }

    /** QR content for an approved application: only an opaque identifier, no personal data. */
    public String getQrPayload() {
        if (status != ApplicationStatus.APPROVED) {
            throw new IllegalStateException("Only approved applications have a verification QR.");
        }
        return QR_PREFIX + getId();
    }

    /** Reads the application id from a scanned QR, or returns null if it is not ours. */
    public static String parseQrPayload(String scanned) {
        if (scanned == null) return null;
        Matcher matcher = QR_PATTERN.matcher(scanned.strip());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String cityName() {
        return city == null ? "the applicant's city" : city.getDisplayName();
    }

    public String getOwnerUid() { return ownerUid; }
    public MetroManilaCity getCity() { return city; }
    public String getApplicantName() { return applicantName; }
    public String getMemberId() { return memberId; }
    public String getMemberName() { return memberName; }
    public NeedCategory getCategory() { return category; }
    public String getJustification() { return justification; }
    public String getProofRef() { return proofRef; }
    public long getSubmittedAt() { return submittedAt; }
    public ApplicationStatus getStatus() { return status; }
    public String getReviewerUid() { return reviewerUid; }
    public String getReviewReason() { return reviewReason; }
    public long getDecidedAt() { return decidedAt; }
    public List<StatusChange> getHistory() { return Collections.unmodifiableList(history); }

    /** Pending for more than a day: shown as "waiting > 24 h" to LGU reviewers. */
    public boolean isWaitingLong(long now) {
        return status == ApplicationStatus.PENDING && now - submittedAt > 24L * 60 * 60 * 1000;
    }
}
