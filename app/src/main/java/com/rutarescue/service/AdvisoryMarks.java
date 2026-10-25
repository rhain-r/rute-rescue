package com.rutarescue.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.rutarescue.model.advisory.Advisory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers, on this device only, which LGU advisories the person marked "Helpful" or
 * "Save update" (the two actions under the Figma "Verified LGU Update" card). Since 2026-10-21 a
 * saved update is listed above all the others in Notifications (every account) and stays there after
 * the 30 days; the Home and Overview lists stay "recent updates", newest first.
 *
 * Saved advisories are already kept offline by the Firestore cache; this class only stores the
 * resident's own marks, so nothing personal leaves the phone (DOCX section 12).
 * Encapsulation: the preference keys and the stored sets are private; callers use the methods.
 */
public final class AdvisoryMarks {

    private static final String FILE = "advisory_marks";
    private static final String KEY_HELPFUL = "helpful";
    private static final String KEY_SAVED = "saved";
    /** "saved_at_<id>": when the advisory was saved, so the latest save is listed first. */
    private static final String SAVED_AT = "saved_at_";
    /** How many times one person sees the "Saved: it now stays at the top" explanation. */
    public static final int SAVE_HINT_LIMIT = 3;
    /** Kept in its own file so sign-out (clear()) does not make the explanation come back. */
    private static final String HINT_FILE = "advisory_save_hint";

    private final SharedPreferences preferences;
    private final SharedPreferences hints;

    public AdvisoryMarks(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.hints = context.getApplicationContext().getSharedPreferences(HINT_FILE, Context.MODE_PRIVATE);
    }

    /**
     * True for the first three saves of this person (then never again), and counts this one: new users
     * learn that a saved update moves to the top, without seeing the same message every time.
     */
    public synchronized boolean shouldExplainSave(String uid) {
        String key = "shown_" + (uid == null ? "" : uid);
        int shown = hints.getInt(key, 0);
        if (shown >= SAVE_HINT_LIMIT) return false;
        hints.edit().putInt(key, shown + 1).apply();
        return true;
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
        boolean saved = toggle(KEY_SAVED, advisoryId);
        if (saved) {
            preferences.edit().putLong(SAVED_AT + advisoryId, System.currentTimeMillis()).apply();
        } else if (advisoryId != null) {
            preferences.edit().remove(SAVED_AT + advisoryId).apply();
        }
        return saved;
    }

    /** When the advisory was saved, or 0 when it is not saved (older saves without a time count as 1). */
    public long savedAt(String advisoryId) {
        if (!isSaved(advisoryId)) return 0L;
        return Math.max(1L, preferences.getLong(SAVED_AT + advisoryId, 1L));
    }

    /** The list as people should see it: saved updates on top, newest save first. */
    public List<Advisory> savedFirst(List<Advisory> newestFirst) {
        return AdvisoryService.savedFirst(newestFirst, this::savedAt);
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
