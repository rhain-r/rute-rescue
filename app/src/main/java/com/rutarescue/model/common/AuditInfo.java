package com.rutarescue.model.common;

/**
 * Who created/changed a record and when (DOCX section 5: every mutation stores the actor
 * and the timestamp). Immutable: an update produces a new AuditInfo object.
 */
public final class AuditInfo {

    private final String createdBy;
    private final long createdAt;
    private final String updatedBy;
    private final long updatedAt;

    public AuditInfo(String createdBy, long createdAt, String updatedBy, long updatedAt) {
        this.createdBy = createdBy == null ? "" : createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy == null ? this.createdBy : updatedBy;
        this.updatedAt = updatedAt;
    }

    public static AuditInfo created(String actorUid, long now) {
        return new AuditInfo(actorUid, now, actorUid, now);
    }

    public AuditInfo updated(String actorUid, long now) {
        return new AuditInfo(createdBy, createdAt, actorUid, now);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
