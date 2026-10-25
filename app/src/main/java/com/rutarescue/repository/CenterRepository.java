package com.rutarescue.repository;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;

import java.util.List;

/** Evacuation centers: public reads of published centers, writes by the city's staff only. */
public interface CenterRepository {

    ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback);

    /** All centers of one city, including drafts (LGU staff of that city only). */
    ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<EvacuationCenter>>> callback);

    void get(String id, Callback<EvacuationCenter> callback);

    /** Follows one center, including whether this phone still has changes to upload. */
    ListenerHandle listen(String id, Callback<CenterSnapshot> callback);

    String newId();

    /** Saves the center together with its audit event in one atomic batch. */
    void save(EvacuationCenter center, AuditEvent audit, Callback<Void> callback);

    /**
     * The LGU's publish or send-back step: writes only the publication fields and the audit event.
     */
    void saveReview(EvacuationCenter center, AuditEvent audit, Callback<Void> callback);

    /**
     * The moderator's capacity screen (2026-10-22): writes only the headcount, status and facility
     * fields and the audit event, because the rules let a moderator change nothing else on a center.
     */
    void saveCapacity(EvacuationCenter center, AuditEvent audit, Callback<Void> callback);

    /** Saves many centers (dataset import) with one audit event each, atomically. */
    void saveAll(List<EvacuationCenter> centers, List<AuditEvent> audits, Callback<Void> callback);
}
