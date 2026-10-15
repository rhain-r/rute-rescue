package com.rutarescue.model.common;

import java.util.UUID;

/**
 * One immutable line in the audit trail: actor, timestamp, action, affected record and
 * reason (DOCX section 5). Audit events are create-only; the security rules forbid
 * updating or deleting them.
 */
public final class AuditEvent {

    private final String id;
    private final String actorUid;
    private final String jurisdiction;
    private final String action;
    private final String entityType;
    private final String recordId;
    private final String reason;
    private final long timestamp;

    public AuditEvent(String id, String actorUid, String jurisdiction, String action,
                      String entityType, String recordId, String reason, long timestamp) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Audit id is required.");
        if (actorUid == null || actorUid.isBlank()) throw new IllegalArgumentException("Audit actor is required.");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("Audit action is required.");
        if (recordId == null || recordId.isBlank()) throw new IllegalArgumentException("Audit record id is required.");
        this.id = id;
        this.actorUid = actorUid;
        this.jurisdiction = jurisdiction == null ? "" : jurisdiction;
        this.action = action;
        this.entityType = entityType == null ? "" : entityType;
        this.recordId = recordId;
        this.reason = reason == null ? "" : reason;
        this.timestamp = timestamp;
    }

    /** Creates an audit event for an entity (which must already have an id). */
    public static AuditEvent create(String actorUid, String jurisdiction, String action,
                                    AuditableEntity entity, String reason, long now) {
        return new AuditEvent(UUID.randomUUID().toString(), actorUid, jurisdiction, action,
                entity.getEntityType(), entity.getId(), reason, now);
    }

    public String getId() { return id; }
    public String getActorUid() { return actorUid; }
    public String getJurisdiction() { return jurisdiction; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getRecordId() { return recordId; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
}
