package com.rutarescue.repository;

import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.common.SourcedData;

import java.util.List;

/** LGU advisories shown on the resident Home screen and inbox. */
public interface AdvisoryRepository {

    ListenerHandle listenPublished(Callback<SourcedData<List<Advisory>>> callback);

    ListenerHandle listenForCity(MetroManilaCity city, Callback<SourcedData<List<Advisory>>> callback);

    void get(String id, Callback<Advisory> callback);

    String newId();

    void save(Advisory advisory, AuditEvent audit, Callback<Void> callback);
}
