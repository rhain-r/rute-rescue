package com.rutarescue.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.SourcedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether data is LIVE, CACHED or STALE (DOCX section 11: "Never pretend that
 * cached information is live").
 *
 * Firestore tells us whether a snapshot came from the server or from the phone's cache.
 * We remember when each kind of data last came from the server, so cached data can show
 * its real age, and old cached data is marked STALE.
 */
public final class FreshnessService {

    public static final String ADVISORIES = "advisories";
    public static final String CENTERS = "centers";
    public static final String FAMILY = "family";
    public static final String LIGTAS = "ligtas";
    public static final String DATASETS = "datasets";

    private static final long HOUR = 60L * 60 * 1000;
    private static final Map<String, Long> STALE_AFTER = new HashMap<>();

    static {
        STALE_AFTER.put(ADVISORIES, 6 * HOUR);
        STALE_AFTER.put(CENTERS, 24 * HOUR);
        STALE_AFTER.put(FAMILY, 7 * 24 * HOUR);
        STALE_AFTER.put(LIGTAS, 24 * HOUR);
        STALE_AFTER.put(DATASETS, 24 * HOUR);
    }

    private final SharedPreferences prefs;

    public FreshnessService(Context context) {
        prefs = context.getSharedPreferences("rr_freshness", Context.MODE_PRIVATE);
    }

    /** Wraps data with its freshness label and the time it was last confirmed by the server. */
    public <T> SourcedData<T> wrap(T data, boolean fromCache, String key, String source) {
        long now = System.currentTimeMillis();
        if (!fromCache) {
            markServerSync(key, now);
            return new SourcedData<>(data, DataFreshness.LIVE, now, source);
        }
        long last = lastServerSync(key);
        long staleAfter = STALE_AFTER.containsKey(key) ? STALE_AFTER.get(key) : 24 * HOUR;
        DataFreshness freshness = last == 0 || now - last > staleAfter ? DataFreshness.STALE : DataFreshness.CACHED;
        return new SourcedData<>(data, freshness, last, source);
    }

    public void markServerSync(String key, long when) {
        prefs.edit().putLong(key, when).apply();
    }

    public long lastServerSync(String key) {
        return prefs.getLong(key, 0L);
    }
}
