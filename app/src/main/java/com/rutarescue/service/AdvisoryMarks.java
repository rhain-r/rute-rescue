package com.rutarescue.service;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers, on this device only, which LGU advisories the resident marked "Helpful" or
 * "Save update" (the two actions under the Figma "Verified LGU Update" card).
 *
 * Saved advisories are already kept offline by the Firestore cache; this class only stores the
 * resident's own marks, so nothing personal leaves the phone (DOCX section 12).
 * Encapsulation: the preference keys and the stored sets are private; callers use the methods.
 */
public final class AdvisoryMarks {

    private static final String FILE = "advisory_marks";
    private static final String KEY_HELPFUL = "helpful";
    private static final String KEY_SAVED = "saved";

    private final SharedPreferences preferences;

    public AdvisoryMarks(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isHelpful(String advisoryId) {
        return contains(KEY_HELPFUL, advisoryId);
    }

    public boolean isSaved(String advisoryId) {
        return contains(KEY_SAVED, advisoryId);
    }

    /** Switches the "Helpful" mark and returns the new state. */
    public boolean toggleHelpful(String advisoryId) {
        return toggle(KEY_HELPFUL, advisoryId);
    }

    /** Switches the "Save update" mark and returns the new state. */
    public boolean toggleSaved(String advisoryId) {
        return toggle(KEY_SAVED, advisoryId);
    }

    /** At sign-out: the marks belong to the person who made them. */
    public void clear() {
        preferences.edit().clear().apply();
    }

    private boolean contains(String key, String advisoryId) {
        return advisoryId != null && preferences.getStringSet(key, new HashSet<>()).contains(advisoryId);
    }

    private synchronized boolean toggle(String key, String advisoryId) {
        if (advisoryId == null || advisoryId.isEmpty()) return false;
        // getStringSet() returns a set we must not modify, so we work on a copy.
        Set<String> ids = new HashSet<>(preferences.getStringSet(key, new HashSet<>()));
        boolean nowMarked = ids.add(advisoryId);
        if (!nowMarked) ids.remove(advisoryId);
        preferences.edit().putStringSet(key, ids).apply();
        return nowMarked;
    }
}
