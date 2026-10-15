package com.rutarescue.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.repository.Callback;

import java.util.List;
import java.util.function.Consumer;

/**
 * Gets the phone's location with the built-in LocationManager (no Google Play services
 * dependency). The precise location is used only on the device and is never logged.
 */
public final class LocationHelper {

    /** A saved fix younger than this is reused; older ones trigger a fresh reading. */
    private static final long FRESH_ENOUGH_MS = 20 * 1000;

    private final Context context;
    private final LocationManager manager;
    private volatile GeoPoint lastKnown;

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Last position seen in this session (may be null). */
    public GeoPoint getLastKnown() {
        return lastKnown;
    }

    /** Delivers a recent fix, or an error when permission is missing / no fix within 10 s. */
    @SuppressLint("MissingPermission")
    public void getCurrent(Callback<GeoPoint> callback) {
        if (!hasPermission()) {
            callback.onError(new SecurityException("Location permission was not granted."));
            return;
        }
        Location best = null;
        List<String> providers = manager.getProviders(true);
        for (String provider : providers) {
            Location location = manager.getLastKnownLocation(provider);
            if (location != null && (best == null || location.getTime() > best.getTime())) best = location;
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < FRESH_ENOUGH_MS) {
            deliver(best, callback);
            return;
        }
        String provider = providers.contains(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : providers.contains(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        if (provider == null) {
            if (best != null) deliver(best, callback);
            else callback.onError(new IllegalStateException("Location is turned off on this phone."));
            return;
        }
        final Location fallback = best;
        Handler handler = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CancellationSignal cancel = new CancellationSignal();
            handler.postDelayed(cancel::cancel, 10_000);
            manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context), location -> {
                if (location != null) deliver(location, callback);
                else if (fallback != null) deliver(fallback, callback);
                else callback.onError(new IllegalStateException("No location fix yet. Try again outdoors."));
            });
        } else {
            // On Android 8-10 these extra methods are abstract, so all of them are implemented.
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    manager.removeUpdates(this);
                    deliver(location, callback);
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
            handler.postDelayed(() -> {
                manager.removeUpdates(listener);
                if (lastKnown == null) {
                    if (fallback != null) deliver(fallback, callback);
                    else callback.onError(new IllegalStateException("No location fix yet. Try again outdoors."));
                }
            }, 10_000);
        }
    }

    /**
     * Continuous position updates for turn-by-turn guidance (every 2 s or 5 m, GPS and network).
     * Android only produces fresh GPS fixes while an app listens, so guidance must use this
     * instead of repeated one-off readings. Call {@link #stopTracking(LocationListener)} when done.
     *
     * @return the listener to pass to stopTracking, or null without location permission
     */
    @SuppressLint("MissingPermission")
    public LocationListener startTracking(Consumer<GeoPoint> onPosition) {
        if (!hasPermission()) return null;
        LocationListener listener = new TrackingListener(location -> {
            GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
            lastKnown = point;
            onPosition.accept(point);
        });
        List<String> providers = manager.getProviders(true);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (providers.contains(provider)) {
                manager.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper());
            }
        }
        return listener;
    }

    public void stopTracking(LocationListener listener) {
        if (listener != null) manager.removeUpdates(listener);
    }

    /** On Android 8-10 every LocationListener method is abstract, so all of them are implemented. */
    private static final class TrackingListener implements LocationListener {
        private final Consumer<Location> onLocation;

        TrackingListener(Consumer<Location> onLocation) {
            this.onLocation = onLocation;
        }

        @Override
        public void onLocationChanged(Location location) {
            onLocation.accept(location);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private void deliver(Location location, Callback<GeoPoint> callback) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        lastKnown = point;
        callback.onSuccess(point);
    }
}
