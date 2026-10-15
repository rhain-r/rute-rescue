package com.rutarescue.model.place;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.Locatable;

/** A destination returned by the search provider. */
public final class PlaceResult implements Locatable {

    private final String name;
    private final String address;
    private final GeoPoint location;

    public PlaceResult(String name, String address, GeoPoint location) {
        this.name = name == null || name.isBlank() ? (address == null ? "Selected place" : address) : name.strip();
        this.address = address == null ? "" : address.strip();
        this.location = location;
    }

    @Override
    public GeoPoint getLocation() { return location; }

    @Override
    public String getDisplayName() { return name; }

    @Override
    public String getSubtitle() { return address; }
}
