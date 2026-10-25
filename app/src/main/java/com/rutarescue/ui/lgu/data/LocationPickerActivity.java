package com.rutarescue.ui.lgu.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.rutarescue.BuildConfig;
import com.rutarescue.R;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.map.MapController;

import java.util.Locale;

/**
 * "Pick on map" (2026-10-20): staff tap the map where the center is instead of typing
 * coordinates. The pin can be moved by tapping again; "Use this location" sends the point back
 * to the center editor. Points outside Metro Manila cannot be used (the editor rejects them too).
 */
public class LocationPickerActivity extends BaseActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    private static final String EXTRA_START_LAT = "start_lat";
    private static final String EXTRA_START_LON = "start_lon";

    private MapController map;
    private GeoPoint picked;

    /** @param start where the map opens (the current pin, or the city hall for a new center) */
    public static Intent intent(Context context, GeoPoint start) {
        return new Intent(context, LocationPickerActivity.class)
                .putExtra(EXTRA_START_LAT, start.getLatitude())
                .putExtra(EXTRA_START_LON, start.getLongitude());
    }

    /** The picked point from the result Intent, or null when nothing was picked. */
    public static GeoPoint result(Intent data) {
        if (data == null || !data.hasExtra(EXTRA_LAT)) return null;
        return new GeoPoint(data.getDoubleExtra(EXTRA_LAT, 0), data.getDoubleExtra(EXTRA_LON, 0));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);
        applySystemBarInsets(findViewById(R.id.picker_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.pick_location_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        findViewById(R.id.picker_use).setOnClickListener(v -> {
            setResult(Activity.RESULT_OK, new Intent()
                    .putExtra(EXTRA_LAT, picked.getLatitude())
                    .putExtra(EXTRA_LON, picked.getLongitude()));
            finish();
        });

        GeoPoint start = new GeoPoint(getIntent().getDoubleExtra(EXTRA_START_LAT, MapController.METRO_MANILA_CENTER.getLatitude()),
                getIntent().getDoubleExtra(EXTRA_START_LON, MapController.METRO_MANILA_CENTER.getLongitude()));
        showPicked(null);
        map = new MapController(findViewById(R.id.picker_map), BuildConfig.TOMTOM_API_KEY);
        map.onCreate(savedInstanceState, ready -> {
            ready.moveTo(start, 16);
            ready.setOnMapClick(this::pick);
        });
    }

    private void pick(GeoPoint point) {
        if (map != null) map.showMarkers(null, null, point);
        showPicked(point);
    }

    private void showPicked(GeoPoint point) {
        TextView text = findViewById(R.id.picker_coordinates);
        boolean usable = point != null && point.isInsideMetroManilaRegion();
        picked = usable ? point : null;
        findViewById(R.id.picker_use).setEnabled(usable);
        if (point == null) {
            text.setText("No pin yet.");
        } else if (!usable) {
            text.setText("That point is outside Metro Manila. Tap inside your city.");
        } else {
            text.setText(String.format(Locale.US, "Pin at %.5f, %.5f", point.getLatitude(), point.getLongitude()));
        }
    }

    @Override protected void onStart() { super.onStart(); if (map != null) map.onStart(); }
    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onStop() { if (map != null) map.onStop(); super.onStop(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (map != null) map.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (map != null) map.onDestroy();
        super.onDestroy();
    }
}
