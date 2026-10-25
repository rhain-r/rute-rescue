package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.service.AdvisoryService;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Advisories older than 30 days drop off every list (they stay in the database). */
public class AdvisoryRecencyTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;

    private static Advisory published(String id, long publishedAt) throws ValidationException {
        return new Advisory(id, "Flood watch " + id, "Stay alert for rising water.", AlertLevel.ADVISORY,
                MetroManilaCity.MANILA, true, publishedAt, "Manila LGU");
    }

    @Test
    public void aPublishedAdvisoryIsRecentForThirtyDays() throws ValidationException {
        assertTrue(published("a", NOW - 29 * DAY).isRecent(NOW));
        assertTrue(published("b", NOW - 30 * DAY).isRecent(NOW));
        assertFalse(published("c", NOW - 30 * DAY - 1).isRecent(NOW));
    }

    @Test
    public void aDraftUsesItsLastChangeTime() throws ValidationException {
        Advisory draft = new Advisory("d", "Draft notice", "Not published yet.", AlertLevel.ADVISORY,
                MetroManilaCity.MANILA, false, 0, "Manila LGU");
        draft.edit("Draft notice", "Edited long ago.", AlertLevel.ADVISORY, "uid-1", NOW - 40 * DAY);
        assertFalse(draft.isRecent(NOW));
        draft.edit("Draft notice", "Edited today.", AlertLevel.ADVISORY, "uid-1", NOW - DAY);
        assertTrue(draft.isRecent(NOW));
        assertEquals(NOW - DAY, draft.getActivityTime());
    }

    @Test
    public void anAdvisoryWithoutAnyDateIsKeptVisible() throws ValidationException {
        assertTrue(published("e", 0).isRecent(NOW));
    }

    @Test
    public void recentOnlyKeepsTheOrderAndDropsOldOnes() throws ValidationException {
        List<Advisory> all = new ArrayList<>();
        all.add(published("new", NOW - DAY));
        all.add(published("old", NOW - 31 * DAY));
        all.add(published("mid", NOW - 10 * DAY));
        List<Advisory> recent = AdvisoryService.recentOnly(all, NOW);
        assertEquals(2, recent.size());
        assertEquals("new", recent.get(0).getId());
        assertEquals("mid", recent.get(1).getId());
        assertEquals(30, AdvisoryService.RECENT_DAYS);
    }

    @Test
    public void homeAndTheLguOverviewShowAnAnnouncementForOneWeek() throws ValidationException {
        assertTrue(published("a", NOW - 7 * DAY).isThisWeek(NOW));
        assertFalse("off Home after 7 days", published("b", NOW - 7 * DAY - 1).isThisWeek(NOW));
        assertTrue("still in Notifications", published("b", NOW - 7 * DAY - 1).isRecent(NOW));
    }

    @Test
    public void latestThisWeekPicksTheNewestPublishedOneOfTheCity() throws ValidationException {
        List<Advisory> all = new ArrayList<>(); // newest first, like the lists from Firestore
        all.add(new Advisory("draft", "Draft", "Not published yet.", AlertLevel.ADVISORY,
                MetroManilaCity.MANILA, false, 0, "Manila LGU"));
        all.add(new Advisory("pasig", "Pasig notice", "Pasig River rising.", AlertLevel.ADVISORY,
                MetroManilaCity.PASIG, true, NOW - DAY, "Pasig LGU"));
        all.add(published("manila", NOW - 2 * DAY));
        all.add(published("old", NOW - 9 * DAY));

        assertEquals("manila", AdvisoryService.latestThisWeek(all, MetroManilaCity.MANILA, true, NOW).getId());
        assertEquals("pasig", AdvisoryService.latestThisWeek(all, MetroManilaCity.MARIKINA, false, NOW).getId());
        assertEquals("the LGU never sees another city's", null,
                AdvisoryService.latestThisWeek(all, MetroManilaCity.MARIKINA, true, NOW));
        assertEquals("nothing this week", null,
                AdvisoryService.latestThisWeek(all, MetroManilaCity.MANILA, true, NOW + 6 * DAY));
    }
}
