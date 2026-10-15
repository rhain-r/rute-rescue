package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.common.MetroManilaCity;

import java.util.List;

/** Read access to the audit trail of one jurisdiction (LGU staff only). */
public interface AuditRepository {

    ListenerHandle listenForCity(MetroManilaCity city, Callback<List<AuditEvent>> callback);
}
