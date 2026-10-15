package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.structure.StructureStatus;

import java.util.Map;

/**
 * LGU-recorded passability evidence for bridges and tunnels. The geometry comes from the
 * prepared OSM dataset; only the status lives in Firestore.
 */
public interface StructureStatusRepository {

    /** Status record as stored in Firestore. */
    final class StatusRecord {
        public final StructureStatus status;
        public final String evidence;
        public final long updatedAt;
        public final String updatedBy;

        public StatusRecord(StructureStatus status, String evidence, long updatedAt, String updatedBy) {
            this.status = status;
            this.evidence = evidence;
            this.updatedAt = updatedAt;
            this.updatedBy = updatedBy;
        }
    }

    ListenerHandle listen(Callback<Map<String, StatusRecord>> callback);

    /** Blocking read for background threads (falls back to the offline cache). */
    Map<String, StatusRecord> loadBlocking();

    void setStatus(String structureId, StatusRecord record, MetroManilaCity city, AuditEvent audit, Callback<Void> callback);
}
