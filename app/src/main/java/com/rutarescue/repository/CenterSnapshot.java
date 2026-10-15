package com.rutarescue.repository;

import com.rutarescue.model.center.EvacuationCenter;

/**
 * One center as the capacity screen sees it, plus whether changes made on this phone are still
 * waiting to upload ("Pending sync" banner). Immutable.
 */
public final class CenterSnapshot {

    private final EvacuationCenter center;
    private final boolean pendingSync;

    public CenterSnapshot(EvacuationCenter center, boolean pendingSync) {
        this.center = center;
        this.pendingSync = pendingSync;
    }

    public EvacuationCenter getCenter() { return center; }
    public boolean isPendingSync() { return pendingSync; }
}
