package com.rutarescue.service;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.common.MetroManilaCity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Saved updates go above all the recent ones, newest save first; saved ones outlive the 30 days. */
public class SavedAdvisoriesTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;

    private static Advisory published(String id, long publishedAt) throws ValidationException {
        return new Advisory(id, "Update " + id, "Stay alert for rising water.", AlertLevel.ADVISORY,
                MetroManilaCity.MANILA, true, publishedAt, "Manila LGU");
    }

    private static List<String> ids(List<Advisory> advisories) {
        List<String> ids = new ArrayList<>();
        for (Advisory advisory : advisories) ids.add(advisory.getId());
        return ids;
    }

    @Test
    public void savedUpdatesGoAboveTheRecentOnesNewestSaveFirst() throws ValidationException {
        List<Advisory> newestFirst = new ArrayList<>();
        newestFirst.add(published("a", NOW - DAY));
        newestFirst.add(published("b", NOW - 2 * DAY));
        newestFirst.add(published("c", NOW - 3 * DAY));
        newestFirst.add(published("d", NOW - 4 * DAY));
        Map<String, Long> savedAt = new HashMap<>();
        savedAt.put("c", NOW - 60_000L);   // saved a minute ago
        savedAt.put("d", NOW - 1_000L);    // saved just now
        List<Advisory> shown = AdvisoryService.savedFirst(newestFirst, id -> savedAt.getOrDefault(id, 0L));
        assertEquals(List.of("d", "c", "a", "b"), ids(shown));
    }

    @Test
    public void nothingSavedKeepsTheNewestFirstOrder() throws ValidationException {
        List<Advisory> newestFirst = new ArrayList<>();
        newestFirst.add(published("a", NOW - DAY));
        newestFirst.add(published("b", NOW - 2 * DAY));
        assertEquals(List.of("a", "b"), ids(AdvisoryService.savedFirst(newestFirst, id -> 0L)));
    }

    @Test
    public void aSavedUpdateStaysAfterThirtyDays() throws ValidationException {
        List<Advisory> all = new ArrayList<>();
        all.add(published("new", NOW - DAY));
        all.add(published("oldSaved", NOW - 45 * DAY));
        all.add(published("oldNotSaved", NOW - 45 * DAY));
        List<Advisory> kept = AdvisoryService.recentOnly(all, NOW, "oldSaved"::equals);
        assertEquals(List.of("new", "oldSaved"), ids(kept));
    }
}
