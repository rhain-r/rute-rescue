package com.rutarescue.model.common;

/**
 * Parent class for every record that must keep an audit trail: advisories, evacuation
 * centers, Ligtas applications, dataset versions, road closures and family records.
 *
 * Inheritance: all of these really "are" auditable records, so they share the id,
 * audit information and archive behaviour defined once here.
 * Abstraction: each subclass must say what kind of entity it is.
 */
public abstract class AuditableEntity {

    private String id;
    private AuditInfo auditInfo;
    private boolean archived;

    protected AuditableEntity(String id, AuditInfo auditInfo, boolean archived) {
        this.id = id;
        this.auditInfo = auditInfo;
        this.archived = archived;
    }

    /** Short machine name used in audit events, e.g. "advisory" or "center". */
    public abstract String getEntityType();

    public String getId() {
        return id;
    }

    /** Assigns the database id once. Changing an existing id is a programming error. */
    public void assignId(String newId) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("Record id must not be blank.");
        }
        if (id != null && !id.equals(newId)) {
            throw new IllegalStateException("Record already has id " + id);
        }
        id = newId;
    }

    public AuditInfo getAuditInfo() {
        return auditInfo;
    }

    public boolean isArchived() {
        return archived;
    }

    /** Archive is preferred over destructive deletion (DOCX section 5). */
    public void archive(String actorUid, long now) {
        archived = true;
        markUpdated(actorUid, now);
    }

    /**
     * Used only by repositories when a saved record is read back from storage, so the
     * original id, audit information and archive flag are restored exactly.
     */
    public void restoreFromStorage(String storedId, AuditInfo storedAudit, boolean storedArchived) {
        if (storedId != null) assignId(storedId);
        auditInfo = storedAudit;
        archived = storedArchived;
    }

    /** Subclasses call this from every method that changes their state. */
    protected final void markUpdated(String actorUid, long now) {
        auditInfo = auditInfo == null ? AuditInfo.created(actorUid, now) : auditInfo.updated(actorUid, now);
    }
}
