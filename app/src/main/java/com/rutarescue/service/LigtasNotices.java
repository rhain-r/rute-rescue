package com.rutarescue.service;

import com.rutarescue.model.ligtas.LigtasApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides which Ligtas status changes the resident should be told about (2026-10-20), in plain
 * Java so it is unit-tested. A change is identified by "status@time"; the phone remembers the
 * ones it already announced (LigtasNotifier keeps them in SharedPreferences).
 */
public final class LigtasNotices {

    /** Changes older than this are not announced on a phone that never saw them (e.g. a new install). */
    static final long ANNOUNCE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;
    /** The Notifications screen lists changes of the last 30 days. */
    static final long INBOX_WINDOW_MS = 30L * 24 * 60 * 60 * 1000;

    private LigtasNotices() {
    }

    /** A decision, a changed decision or a resubmission: anything after the first submission. */
    public static boolean hasNews(LigtasApplication application) {
        return application.getHistory().size() > 1;
    }

    static String key(LigtasApplication application) {
        return application.getStatus().name() + "@" + application.getStatusChangedAt();
    }

    /** The applications whose latest change is new to this phone and recent enough to announce. */
    public static List<LigtasApplication> toAnnounce(List<LigtasApplication> applications, Map<String, String> seen, long now) {
        List<LigtasApplication> news = new ArrayList<>();
        for (LigtasApplication application : applications) {
            if (!hasNews(application) || !application.isDecided()) continue; // only the moderator's answers
            if (key(application).equals(seen.get(application.getId()))) continue;
            if (now - application.getStatusChangedAt() > ANNOUNCE_WINDOW_MS) continue;
            news.add(application);
        }
        return news;
    }

    /** Remembers that these changes were announced. */
    public static void markSeen(List<LigtasApplication> announced, Map<String, String> seen) {
        for (LigtasApplication application : announced) seen.put(application.getId(), key(application));
    }

    /** The status changes for the Notifications screen: last 30 days, newest first. */
    public static List<LigtasApplication> forInbox(List<LigtasApplication> applications, long now) {
        List<LigtasApplication> notices = new ArrayList<>();
        for (LigtasApplication application : applications) {
            if (hasNews(application) && now - application.getStatusChangedAt() <= INBOX_WINDOW_MS) notices.add(application);
        }
        notices.sort((a, b) -> Long.compare(b.getStatusChangedAt(), a.getStatusChangedAt()));
        return notices;
    }
}
