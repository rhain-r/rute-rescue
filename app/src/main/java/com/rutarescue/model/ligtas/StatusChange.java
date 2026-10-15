package com.rutarescue.model.ligtas;

/** One entry in an application's history: who changed the status, when and why. */
public final class StatusChange {

    private final ApplicationStatus status;
    private final String actorUid;
    private final String reason;
    private final long at;

    public StatusChange(ApplicationStatus status, String actorUid, String reason, long at) {
        this.status = status;
        this.actorUid = actorUid == null ? "" : actorUid;
        this.reason = reason == null ? "" : reason;
        this.at = at;
    }

    public ApplicationStatus getStatus() { return status; }
    public String getActorUid() { return actorUid; }
    public String getReason() { return reason; }
    public long getAt() { return at; }
}
