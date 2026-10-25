package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.advisory.Advisory;
import com.rutarescue.model.advisory.AdvisoryReview;
import com.rutarescue.model.advisory.AlertLevel;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 2026-10-22: a moderator's advisory about their center goes Pending -> Approved (published) or
 * Declined (with the LGU's comment). A declined advisory can never be published; a new one is written.
 */
public class AdvisoryApprovalTest {

    private static final long NOW = 1_800_000_000_000L;
    private final ModeratorProfile moderator = new ModeratorProfile("mod-1", "Ana Cruz", "", MetroManilaCity.MANILA,
            "Staff", "c1", "Almario Elementary School");
    private final ModeratorProfile otherCenter = new ModeratorProfile("mod-2", "Carl Lim", "", MetroManilaCity.MANILA,
            "Staff", "c2", "Delpan Gym");
    private final LguProfile lgu = new LguProfile("lgu-1", "Ben Reyes", "", MetroManilaCity.MANILA, "DRRM Officer");
    private final LguProfile pasigLgu = new LguProfile("lgu-2", "Pasig", "", MetroManilaCity.PASIG, "Officer");

    private Advisory pending() throws ValidationException {
        return Advisory.forApproval("a1", moderator, "Center is almost full", "Almario school has room for 20 more people.", NOW);
    }

    @Test
    public void aNewAdvisoryIsPendingAboutTheModeratorsCenter() throws ValidationException {
        Advisory advisory = pending();
        assertEquals(AdvisoryReview.PENDING, advisory.getReview());
        assertTrue(advisory.isAwaitingLgu());
        assertFalse("residents cannot see it yet", advisory.isPublished());
        assertEquals("c1", advisory.getCenterId());
        assertEquals("Almario Elementary School", advisory.getCenterName());
        assertEquals("Ana Cruz", advisory.getRequestedBy());
        assertEquals("only the LGU sets levels", AlertLevel.ADVISORY, advisory.getLevel());
        assertEquals("Pending approval", advisory.getStateLabel());
    }

    @Test
    public void aModeratorWithoutACenterCannotWriteOne() {
        ModeratorProfile unassigned = new ModeratorProfile("mod-3", "New", "", MetroManilaCity.MANILA, "Staff");
        assertThrows(IllegalStateException.class, () -> Advisory.forApproval("a2", unassigned, "Title here",
                "Some message text.", NOW));
    }

    @Test
    public void acceptingPublishesItWithTheCityAlertLevel() throws ValidationException {
        Advisory advisory = pending();
        assertThrows("a moderator cannot accept", IllegalStateException.class,
                () -> advisory.approve(moderator, AlertLevel.ALERT_2, NOW + 1));
        assertThrows("another city's LGU cannot accept", IllegalStateException.class,
                () -> advisory.approve(pasigLgu, AlertLevel.ALERT_2, NOW + 1));
        advisory.approve(lgu, AlertLevel.ALERT_2, NOW + 1);
        assertTrue(advisory.isPublished());
        assertEquals(AdvisoryReview.APPROVED, advisory.getReview());
        assertEquals(AlertLevel.ALERT_2, advisory.getLevel());
        assertEquals(NOW + 1, advisory.getPublishedAt());
        assertEquals("Approved", advisory.getStateLabel());
        assertThrows("decided once", IllegalStateException.class, () -> advisory.decline(lgu, "Too late now", NOW + 2));
    }

    @Test
    public void aDeclineNeedsACommentAndLocksTheAdvisory() throws ValidationException {
        Advisory advisory = pending();
        assertThrows(ValidationException.class, () -> advisory.decline(lgu, " ", NOW + 1));
        advisory.decline(lgu, "Please add how many people it can still take.", NOW + 1);
        assertEquals(AdvisoryReview.DECLINED, advisory.getReview());
        assertEquals("Please add how many people it can still take.", advisory.getLguNote());
        assertEquals("Declined", advisory.getStateLabel());
        assertFalse(advisory.isPublished());
        assertThrows("never published as is", IllegalStateException.class, () -> advisory.publish("lgu-1", NOW + 2));
        assertThrows(IllegalStateException.class, () -> advisory.approve(lgu, AlertLevel.ADVISORY, NOW + 2));
        assertThrows(IllegalStateException.class, () -> advisory.editPending(moderator, "New title", "New message text.", NOW + 2));
        assertThrows(IllegalStateException.class, () -> advisory.edit("New title", "New message text.", AlertLevel.ADVISORY, "lgu-1", NOW + 2));
    }

    @Test
    public void afterADeclineTheModeratorWritesANewOne() throws ValidationException {
        Advisory declined = pending();
        declined.decline(lgu, "Please add the number of free beds.", NOW + 1);
        Advisory again = declined.startResubmission("a9", moderator, NOW + 2);
        assertNotEquals(declined.getId(), again.getId());
        assertEquals(AdvisoryReview.PENDING, again.getReview());
        assertEquals("starts from the old text", declined.getMessage(), again.getMessage());
        assertEquals("the declined one stays declined", AdvisoryReview.DECLINED, declined.getReview());
        assertThrows("only a declined one is written again", IllegalStateException.class,
                () -> again.startResubmission("a10", moderator, NOW + 3));
    }

    @Test
    public void onlyTheSameCentersModeratorFixesAPendingAdvisory() throws ValidationException {
        Advisory advisory = pending();
        advisory.editPending(moderator, "Center is full", "Almario school is full; go to Delpan Gym.", NOW + 1);
        assertEquals("Center is full", advisory.getTitle());
        assertThrows(IllegalStateException.class, () -> advisory.editPending(otherCenter, "Changed title",
                "Changed message text.", NOW + 2));
    }

    @Test
    public void advisoriesSavedBefore2026_10_22AreReadIntoTheNewStates() {
        assertEquals(AdvisoryReview.PENDING, AdvisoryReview.fromStored("", true, "", false, "Ana Cruz"));
        assertEquals("a draft sent back with a note is declined", AdvisoryReview.DECLINED,
                AdvisoryReview.fromStored(null, false, "Add the address", false, "Ana Cruz"));
        assertEquals(AdvisoryReview.APPROVED, AdvisoryReview.fromStored("", false, "", true, "Ana Cruz"));
        assertEquals("the LGU's own advisory", AdvisoryReview.NONE, AdvisoryReview.fromStored("", false, "", true, ""));
        assertEquals(AdvisoryReview.DECLINED, AdvisoryReview.fromStored("DECLINED", false, "x", false, ""));
    }
}
