package com.rutarescue;

import android.app.Application;
import android.content.Context;

import com.rutarescue.service.SyncWorker;

import org.maplibre.android.MapLibre;

/**
 * Application entry point: initialises MapLibre, builds the AppContainer once, and
 * schedules the background sync when Firebase is configured.
 */
public final class RutaRescueApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        MapLibre.getInstance(this);
        container = new AppContainer(this);
        if (container.isFirebaseReady()) {
            SyncWorker.schedule(this);
        }
    }

    /** Convenience accessor used by Activities and Fragments. */
    public static AppContainer container(Context context) {
        return ((RutaRescueApp) context.getApplicationContext()).container;
    }
}
