package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.ligtas.LigtasApplication;

import java.util.List;

/** Ligtas Priority applications. Residents see only their own; city staff only their city's. */
public interface LigtasRepository {

    String newId();

    ListenerHandle listenMine(String ownerUid, Callback<SourcedData<List<LigtasApplication>>> callback);

    ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<LigtasApplication>>> callback);

    ListenerHandle listenOne(String id, Callback<SourcedData<LigtasApplication>> callback);

    void submit(LigtasApplication application, Callback<Void> callback);

    /** Stores the reviewer's decision, history and the audit event in one atomic batch. */
    void saveDecision(LigtasApplication application, AuditEvent audit, Callback<Void> callback);

    /** The resident's new proof after "Needs documents": only status, proof and history change. */
    void saveResubmission(LigtasApplication application, Callback<Void> callback);
}
