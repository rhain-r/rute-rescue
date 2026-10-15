package com.rutarescue.repository;

import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyRecord;

/** Storage of the resident's family evacuation record. */
public interface FamilyRepository {

    /** Delivers null data when the resident has no record yet. */
    void load(String ownerUid, Callback<SourcedData<FamilyRecord>> callback);

    /** Saves the record; when offline the write is queued and synced later. */
    void save(FamilyRecord record, Callback<Void> callback);
}
