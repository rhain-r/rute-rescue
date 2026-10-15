package com.rutarescue.repository;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;

import java.util.List;

/** Evacuation centers: public reads of published centers, LGU-only writes. */
public interface CenterRepository {

    ListenerHandle listenPublished(Callback<SourcedData<List<EvacuationCenter>>> callback);

    /** All centers of one city, including drafts (LGU staff of that city only). */
    ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<EvacuationCenter>>> callback);

    void get(String id, Callback<EvacuationCenter> callback);

    String newId();

    /** Saves the center together with its audit event in one atomic batch. */
    void save(EvacuationCenter center, AuditEvent audit, Callback<Void> callback);

    /** Saves many centers (dataset import) with one audit event each, atomically. */
    void saveAll(List<EvacuationCenter> centers, List<AuditEvent> audits, Callback<Void> callback);
}
