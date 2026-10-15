package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.hazard.DatasetVersion;

import java.util.List;

/** Versions of LGU-managed datasets (center imports, structure status sets). */
public interface DatasetRepository {

    String newId();

    ListenerHandle listen(Callback<List<DatasetVersion>> callback);

    void save(DatasetVersion version, AuditEvent audit, Callback<Void> callback);
}
