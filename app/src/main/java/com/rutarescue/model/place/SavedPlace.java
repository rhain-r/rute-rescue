package com.rutarescue.model.place;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;
import com.rutarescue.validation.TextRules;

/** A quick-access place a resident saved (ported from the legacy saved-locations idea). */
public final class SavedPlace implements Locatable {

    private final String id;
    private final String name;
    private final GeoPoint location;
    private final long createdAt;

    public SavedPlace(String id, String name, GeoPoint location, long createdAt) throws ValidationException {
        this.id = id;
        this.name = TextRules.requiredText("placeName", name);
        TextRules.checkLength("placeName", this.name, 1, 60);
        if (location == null) throw new ValidationException("location", "A saved place needs a location.");
        this.location = location;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public GeoPoint getLocation() { return location; }

    @Override
    public String getDisplayName() { return name; }

    @Override
    public String getSubtitle() { return "Saved place"; }
}
