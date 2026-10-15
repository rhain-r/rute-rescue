package com.rutarescue.repository;

import com.rutarescue.model.common.AuditEvent;
import com.rutarescue.model.route.RoadClosure;

import java.util.List;

/** Active road closures reported by LGU field staff. */
public interface ClosureRepository {

    String newId();

    ListenerHandle listenActive(Callback<List<RoadClosure>> callback);

    /** Blocking read for background threads (falls back to the offline cache). */
    List<RoadClosure> loadActiveBlocking();

    void save(RoadClosure closure, AuditEvent audit, Callback<Void> callback);
}
