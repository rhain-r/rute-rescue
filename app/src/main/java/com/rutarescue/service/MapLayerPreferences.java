package com.rutarescue.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.rutarescue.model.map.MapLayerSettings;

/**
 * Remembers the "Map layers" choices on this phone (Phase 1: "Choices are remembered on the
 * phone"). The resident map and the LGU map keep separate choices.
 * Encapsulation: the preference file and keys are private; callers load and save whole settings.
 */
public final class MapLayerPreferences {

    public static final String RESIDENT_MAP = "resident";
    public static final String LGU_MAP = "lgu";

    private static final String FILE = "map_layers";

    private final SharedPreferences preferences;

    public MapLayerPreferences(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public MapLayerSettings load(String mapName) {
        return MapLayerSettings.decode(preferences.getString(mapName, null));
    }

    public void save(String mapName, MapLayerSettings settings) {
        preferences.edit().putString(mapName, settings.encode()).apply();
    }
}
