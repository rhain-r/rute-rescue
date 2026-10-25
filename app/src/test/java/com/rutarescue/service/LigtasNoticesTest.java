package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.ApplicationStatus;
import com.rutarescue.model.ligtas.LigtasApplication;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.ModeratorProfile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Which Ligtas changes the resident still has to be told about (phone notification + Notifications). */
public class LigtasNoticesTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;
    private final ModeratorProfile moderator = new ModeratorProfile("mod-1", "Ana", "", MetroManilaCity.MANILA, "Staff");

    private static LigtasApplication submitted(String id, long at) throws ValidationException {
        return LigtasApplication.submit(id, "resident-1", MetroManilaCity.MANILA, "Maria", "m1", "Lola",
                NeedCategory.SENIOR_CITIZEN, "Needs help walking to the center.", "proof", "", at);
    }

    @Test
    public void aNewDecisionIsAnnouncedOnce() throws Exception {
        LigtasApplication app = submitted("AppOne12345", NOW - DAY);
        Map<String, String> seen = new HashMap<>();
        assertTrue("a pending application is not news", LigtasNotices.toAnnounce(list(app), seen, NOW).isEmpty());

        app.askForDocuments(moderator, "Please attach the certificate.", NOW - 1000);
        List<LigtasApplication> news = LigtasNotices.toAnnounce(list(app), seen, NOW);
        assertEquals(1, news.size());
        LigtasNotices.markSeen(news, seen);
        assertTrue("not twice", LigtasNotices.toAnnounce(list(app), seen, NOW).isEmpty());

        app.approve(moderator, "Thank you, approved.", NOW - 500); // the decision changed again
        assertEquals(1, LigtasNotices.toAnnounce(list(app), seen, NOW).size());
    }

    @Test
    public void oldDecisionsAreNotAnnouncedOnANewPhone() throws Exception {
        LigtasApplication app = submitted("AppTwo12345", NOW - 20 * DAY);
        app.approve(moderator, "Approved long ago.", NOW - 10 * DAY);
        assertTrue(LigtasNotices.toAnnounce(list(app), new HashMap<>(), NOW).isEmpty());
    }

    @Test
    public void theInboxListsRecentChangesNewestFirst() throws Exception {
        LigtasApplication older = submitted("AppOld12345", NOW - 5 * DAY);
        older.reject(moderator, "Not in our city records.", NOW - 4 * DAY);
        LigtasApplication newer = submitted("AppNew12345", NOW - 3 * DAY);
        newer.approve(moderator, "Approved for the demo.", NOW - DAY);
        LigtasApplication waiting = submitted("AppWait1234", NOW - DAY);
        List<LigtasApplication> notices = LigtasNotices.forInbox(list(older, newer, waiting), NOW);
        assertEquals(2, notices.size());
        assertEquals("AppNew12345", notices.get(0).getId());
        assertEquals(ApplicationStatus.REJECTED, notices.get(1).getStatus());
    }

    private static List<LigtasApplication> list(LigtasApplication... applications) {
        List<LigtasApplication> list = new ArrayList<>();
        for (LigtasApplication application : applications) list.add(application);
        return list;
    }
}
