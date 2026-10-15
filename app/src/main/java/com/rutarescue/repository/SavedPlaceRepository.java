package com.rutarescue.repository;

import com.rutarescue.model.place.SavedPlace;

import java.util.List;

/** Quick-access places saved by a resident (users/{uid}/places). */
public interface SavedPlaceRepository {

    ListenerHandle listen(String uid, Callback<List<SavedPlace>> callback);

    void add(String uid, SavedPlace place, Callback<Void> callback);

    void delete(String uid, String placeId, Callback<Void> callback);
}
