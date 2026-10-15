package com.rutarescue.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Watches the internet connection and notifies screens when it changes (event handling).
 * Features that need the internet (TomTom search, new routes) are disabled while offline.
 */
public final class ConnectivityMonitor {

    /** Listener for connection changes; always called on the main thread. */
    public interface Listener {
        void onConnectivityChanged(boolean online);
    }

    private final ConnectivityManager manager;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean online;

    public ConnectivityMonitor(Context context) {
        manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        online = checkNow();
        manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                update(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            }

            @Override
            public void onLost(Network network) {
                // The DEFAULT network was lost, so there is none now. (Asking getActiveNetwork() here
                // can still return the network being lost, which left the app thinking it was online.)
                // If another network takes over, onCapabilitiesChanged reports it right after.
                update(false);
            }
        });
    }

    private boolean checkNow() {
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void update(boolean nowOnline) {
        if (nowOnline == online) return;
        online = nowOnline;
        mainHandler.post(() -> {
            for (Listener listener : listeners) listener.onConnectivityChanged(nowOnline);
        });
    }

    public boolean isOnline() {
        return online;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
