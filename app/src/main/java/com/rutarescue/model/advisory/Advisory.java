package com.rutarescue.model.advisory;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.ModeratorProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.validation.TextRules;

/**
 * A verified community update shown on the resident Home screen and in Notifications.
 *
 * Two kinds (2026-10-22):
 *  - the LGU's own advisory (review NONE), published directly by the LGU;
 *  - a moderator's advisory about their evacuation center, which waits for the LGU:
 *    PENDING -> APPROVED (published) or DECLINED (with the LGU's comment). A declined advisory is
 *    locked for good: the moderator writes a new one instead (see startResubmission).
 *
 * Encapsulation: the review state only changes through forApproval(), approve() and decline(),
 * and the alert level is only set by the LGU (approve() and the LGU's own advisories).
 */
public class Advisory extends AuditableEntity {

    /** Advisories are shown for 30 days after they were published (or last changed, for drafts). */
    public static final int RECENT_DAYS = 30;
    private static final long RECENT_WINDOW_MS = RECENT_DAYS * 24L * 60 * 60 * 1000;

    private String title;
    private String message;
    private AlertLevel level;
    private final MetroManilaCity city;
    private boolean published;
    private long publishedAt;
    private final String authorName;
    private AdvisoryReview review = AdvisoryReview.NONE;
    /** The LGU's comment when it declined the advisory ("" otherwise). */
    private String lguNote = "";
    /** The moderator who wrote it ("" when the LGU wrote it). */
    private String requestedBy = "";
    private String requestedByUid = "";
    /** The evacuation center the advisory is about ("" for a city-wide LGU advisory). */
    private String centerId = "";
    private String centerName = "";

    public Advisory(String id, String title, String message, AlertLevel level, MetroManilaCity city,
                    boolean published, long publishedAt, String authorName) throws ValidationException {
        super(id, null, false);
        if (city == null) {
            throw new ValidationException("city", "An advisory needs the issuing city.");
        }
        this.city = city;
        this.authorName = authorName == null || authorName.isBlank() ? city.getDisplayName() + " LGU" : authorName.strip();
        applyValidated(title, message, level);
        this.published = published;
        this.publishedAt = publishedAt;
    }

    /**
     * A moderator's new advisory about their own center. It waits for the LGU (PENDING) and has no
     * alert level of its own: only the LGU sets levels.
     */
    public static Advisory forApproval(String id, ModeratorProfile moderator, String title, String message, long now)
            throws ValidationException {
        if (moderator == null || !moderator.canSubmitAdvisories()) {
            throw new IllegalStateException("Ask your LGU to assign you to an evacuation center first.");
        }
        Advisory advisory = new Advisory(id, title, message, AlertLevel.ADVISORY, moderator.getJurisdiction(), false, 0,
                moderator.getCenterName());
        advisory.review = AdvisoryReview.PENDING;
        advisory.requestedBy = moderator.getDisplayName();
        advisory.requestedByUid = moderator.getUid();
        advisory.centerId = moderator.getCenterId();
        advisory.centerName = moderator.getCenterName();
        advisory.markUpdated(moderator.getUid(), now);
        return advisory;
    }

    @Override
    public String getEntityType() {
        return "advisory";
    }

    /** The LGU edits its own advisory (a moderator's advisory is accepted or declined as written). */
    public void edit(String newTitle, String newMessage, AlertLevel newLevel, String actorUid, long now)
            throws ValidationException {
        if (review == AdvisoryReview.DECLINED) {
            throw new IllegalStateException("A declined advisory cannot be changed or published. Write a new one.");
        }
        applyValidated(newTitle, newMessage, newLevel);
        markUpdated(actorUid, now);
    }

    /** The moderator fixes their advisory while it is still waiting for the LGU. */
    public void editPending(ModeratorProfile moderator, String newTitle, String newMessage, long now)
            throws ValidationException {
        if (review != AdvisoryReview.PENDING) {
            throw new IllegalStateException(review == AdvisoryReview.DECLINED
                    ? "A declined advisory cannot be changed. Write a new advisory instead."
                    : "Only an advisory that is still pending can be changed.");
        }
        if (moderator == null || !moderator.hasAssignedCenter() || !moderator.getCenterId().equals(centerId)) {
            throw new IllegalStateException("Only the moderators of " + centerName + " can change this advisory.");
        }
        applyValidated(newTitle, newMessage, AlertLevel.ADVISORY);
        markUpdated(moderator.getUid(), now);
    }

    private void applyValidated(String newTitle, String newMessage, AlertLevel newLevel) throws ValidationException {
        String checkedTitle = TextRules.requiredText("title", newTitle);
        TextRules.checkLength("title", checkedTitle, 3, 80);
        String checkedMessage = TextRules.multilineText("message", newMessage, 5, 600);
        title = checkedTitle;
        message = checkedMessage;
        level = newLevel == null ? AlertLevel.ADVISORY : newLevel;
    }

    /** The LGU publishes its own advisory. */
    public void publish(String actorUid, long now) {
        if (review == AdvisoryReview.DECLINED) {
            throw new IllegalStateException("A declined advisory can never be published. Write a new one.");
        }
        if (review == AdvisoryReview.PENDING) review = AdvisoryReview.APPROVED;
        published = true;
        publishedAt = now;
        lguNote = "";
        markUpdated(actorUid, now);
    }

    /**
     * The LGU accepts a moderator's pending advisory: it is published to residents at once and
     * carries the city's current alert level (set by the LGU).
     */
    public void approve(StaffProfile lgu, AlertLevel cityLevel, long now) {
        checkLgu(lgu);
        if (review != AdvisoryReview.PENDING) {
            throw new IllegalStateException("Only a pending advisory can be accepted.");
        }
        review = AdvisoryReview.APPROVED;
        level = cityLevel == null ? AlertLevel.ADVISORY : cityLevel;
        published = true;
        publishedAt = now;
        lguNote = "";
        markUpdated(lgu.getUid(), now);
    }

    /** The LGU declines it with a comment the moderator reads. It can never be published afterwards. */
    public void decline(StaffProfile lgu, String comment, long now) throws ValidationException {
        checkLgu(lgu);
        if (review != AdvisoryReview.PENDING) {
            throw new IllegalStateException("Only a pending advisory can be declined.");
        }
        lguNote = TextRules.multilineText("comment", comment, 5, 300);
        review = AdvisoryReview.DECLINED;
        published = false;
        markUpdated(lgu.getUid(), now);
    }

    private void checkLgu(StaffProfile lgu) {
        if (lgu == null || !lgu.canPublishToResidents() || !lgu.canManage(city)) {
            throw new IllegalStateException("Only the " + city.getDisplayName() + " LGU can accept or decline this advisory.");
        }
    }

    /**
     * After a decline: a NEW advisory for the same center, starting from the old text so the
     * moderator only fixes what the LGU commented on. The declined one stays as it is.
     */
    public Advisory startResubmission(String newId, ModeratorProfile moderator, long now) throws ValidationException {
        if (review != AdvisoryReview.DECLINED) {
            throw new IllegalStateException("Only a declined advisory is written again.");
        }
        return forApproval(newId, moderator, title, message, now);
    }

    /** Used by the repository when it reads a stored advisory (no audit change). */
    public void restoreReview(AdvisoryReview storedReview, String storedNote, String storedRequestedBy,
                              String storedRequestedByUid, String storedCenterId, String storedCenterName) {
        review = storedReview == null ? AdvisoryReview.NONE : storedReview;
        lguNote = storedNote == null ? "" : storedNote;
        requestedBy = storedRequestedBy == null ? "" : storedRequestedBy;
        requestedByUid = storedRequestedByUid == null ? "" : storedRequestedByUid;
        centerId = storedCenterId == null ? "" : storedCenterId;
        centerName = storedCenterName == null ? "" : storedCenterName;
    }

    /** "Published", "Approved", "Pending approval", "Declined" or "Hidden", for staff lists. */
    public String getStateLabel() {
        if (review == AdvisoryReview.PENDING || review == AdvisoryReview.DECLINED) return review.getLabel();
        if (published) return review == AdvisoryReview.APPROVED ? "Approved" : "Published";
        return "Hidden";
    }

    /** The LGU hides an advisory from residents (it stays in the records). */
    public void unpublish(String actorUid, long now) {
        published = false;
        markUpdated(actorUid, now);
    }

    /**
     * When this advisory last mattered: the publish time of a published advisory, otherwise the
     * last change. 0 when no time was ever recorded.
     */
    public long getActivityTime() {
        if (published && publishedAt > 0) return publishedAt;
        return getAuditInfo() == null ? publishedAt : getAuditInfo().getUpdatedAt();
    }

    /**
     * True for advisories from the last 30 days (the overview, "See all advisories" and the
     * residents' screens hide older ones). One without any recorded time is kept visible.
     */
    public boolean isRecent(long now) {
        long time = getActivityTime();
        return time <= 0 || now - time <= RECENT_WINDOW_MS;
    }

    /** Home and the LGU Overview show only an announcement from the last 7 days (2026-10-25). */
    public static final int LATEST_DAYS = 7;

    /** True for an announcement of the last 7 days (Notifications still keep 30 days). */
    public boolean isThisWeek(long now) {
        long time = getActivityTime();
        return time <= 0 || now - time <= LATEST_DAYS * 24L * 60 * 60 * 1000;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public AlertLevel getLevel() { return level; }
    public MetroManilaCity getCity() { return city; }
    public boolean isPublished() { return published; }
    public long getPublishedAt() { return publishedAt; }
    public String getAuthorName() { return authorName; }
    public AdvisoryReview getReview() { return review; }
    /** True while a moderator's advisory waits for the LGU's Accept or Decline. */
    public boolean isAwaitingLgu() { return review == AdvisoryReview.PENDING; }
    public boolean isDeclined() { return review == AdvisoryReview.DECLINED; }
    /** The LGU's decline comment ("" when there is none). */
    public String getLguNote() { return lguNote; }
    /** The moderator who wrote it ("" when the LGU wrote it). */
    public String getRequestedBy() { return requestedBy; }
    public String getRequestedByUid() { return requestedByUid; }
    /** True for a moderator's advisory about one evacuation center. */
    public boolean isAboutCenter() { return !centerId.isEmpty(); }
    public String getCenterId() { return centerId; }
    public String getCenterName() { return centerName; }
}
