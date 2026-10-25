package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterState;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.LguProfile;
import com.rutarescue.model.user.ModeratorProfile;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Centers moderators sent to the LGU before 2026-10-22 can still be published or sent back by the
 * LGU of the same city (since then the LGU creates centers itself). The advisory approval flow is
 * tested in AdvisoryApprovalTest.
 */
public class LguHandOffTest {

    private static final long NOW = 1_800_000_000_000L;
    private final ModeratorProfile moderator = new ModeratorProfile("mod-1", "Ana Cruz", "", MetroManilaCity.MANILA, "Staff");
    private final LguProfile lgu = new LguProfile("lgu-1", "Ben Reyes", "", MetroManilaCity.MANILA, "DRRM Officer");
    private final LguProfile pasigLgu = new LguProfile("lgu-2", "Pasig", "", MetroManilaCity.PASIG, "Officer");

    private static EvacuationCenter verifiedCenter() throws ValidationException {
        return new EvacuationCenter("c1", "Almario Elementary School", "Tondo", MetroManilaCity.MANILA,
                new GeoPoint(14.60, 120.98), CenterStatus.OPEN, EnumSet.noneOf(AccessibilityFeature.class),
                VerificationInfo.verified("Barangay field check", "mod-1", NOW), false, "");
    }

    @Test
    public void aVerifiedCenterWaitsForTheLguThenIsPublished() throws ValidationException {
        EvacuationCenter center = verifiedCenter();
        assertEquals(CenterState.DRAFT, CenterState.of(center));
        center.sendToLgu("mod-1", NOW);
        assertEquals(CenterState.WAITING_FOR_LGU, CenterState.of(center));
        assertFalse(center.isPublished());
        assertThrows(IllegalStateException.class, () -> center.publishByLgu(moderator, NOW + 1));
        assertThrows(IllegalStateException.class, () -> center.publishByLgu(pasigLgu, NOW + 1));
        center.publishByLgu(lgu, NOW + 1);
        assertTrue(center.isPublished());
        assertEquals(CenterState.PUBLISHED, CenterState.of(center));
    }

    @Test
    public void theLguCanSendACenterBackWithANote() throws ValidationException {
        EvacuationCenter center = verifiedCenter();
        center.sendToLgu("mod-1", NOW);
        assertThrows(ValidationException.class, () -> center.sendBack(lgu, "", NOW + 1));
        center.sendBack(lgu, "The pin is on the wrong street.", NOW + 1);
        assertEquals(CenterState.SENT_BACK, CenterState.of(center));
        assertEquals("The pin is on the wrong street.", center.getLguNote());
        center.sendToLgu("mod-1", NOW + 2); // fixed and sent again
        assertEquals(CenterState.WAITING_FOR_LGU, CenterState.of(center));
        assertEquals("", center.getLguNote());
    }

    @Test
    public void anUnverifiedCenterCannotBeSentToTheLgu() throws ValidationException {
        EvacuationCenter center = new EvacuationCenter("c2", "New Court", "Tondo", MetroManilaCity.MANILA,
                new GeoPoint(14.60, 120.98), CenterStatus.OPEN, EnumSet.noneOf(AccessibilityFeature.class),
                VerificationInfo.unverified("Entered by staff"), false, "");
        assertThrows(IllegalStateException.class, () -> center.sendToLgu("mod-1", NOW));
    }

    @Test
    public void waitingAndSentBackCentersAreListedFirst() {
        assertTrue(CenterState.SENT_BACK.compareTo(CenterState.PUBLISHED) < 0);
        assertTrue(CenterState.WAITING_FOR_LGU.compareTo(CenterState.DRAFT) < 0);
        assertFalse(CenterState.WAITING_FOR_LGU.isVisibleToResidents());
    }
}
