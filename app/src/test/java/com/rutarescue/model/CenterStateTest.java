package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterState;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Where a saved center is in its life: staff see this as a chip on every center row. */
public class CenterStateTest {

    private static EvacuationCenter center(String name, boolean verified, boolean published) throws ValidationException {
        VerificationInfo info = verified ? VerificationInfo.verified("Barangay field check", "uid-1", 1_000L)
                : VerificationInfo.unverified("Entered by staff");
        return new EvacuationCenter("id-" + name, name, "Tondo", MetroManilaCity.MANILA, new GeoPoint(14.60, 120.98),
                CenterStatus.OPEN, EnumSet.noneOf(AccessibilityFeature.class), info, published, "");
    }

    @Test
    public void eachSavedCenterHasOneState() throws ValidationException {
        assertEquals(CenterState.NEEDS_VERIFICATION, CenterState.of(center("Alpha Hall", false, false)));
        assertEquals(CenterState.DRAFT, CenterState.of(center("Beta Hall", true, false)));
        assertEquals(CenterState.PUBLISHED, CenterState.of(center("Gamma Hall", true, true)));
        EvacuationCenter archived = center("Delta Hall", true, true);
        archived.setPublished(false, "uid-1", 2_000L);
        archived.archive("uid-1", 2_000L);
        assertEquals(CenterState.ARCHIVED, CenterState.of(archived));
    }

    @Test
    public void onlyPublishedCentersAreSeenByResidents() {
        assertTrue(CenterState.PUBLISHED.isVisibleToResidents());
        assertFalse(CenterState.DRAFT.isVisibleToResidents());
        assertFalse(CenterState.NEEDS_VERIFICATION.isVisibleToResidents());
        assertFalse(CenterState.ARCHIVED.isVisibleToResidents());
    }

    @Test
    public void centersThatNeedActionAreListedFirstThenByName() throws ValidationException {
        List<EvacuationCenter> centers = new ArrayList<>();
        centers.add(center("Zeta Gym", true, true));
        centers.add(center("Alpha School", true, true));
        centers.add(center("Hidden Hall", true, false));
        centers.add(center("New Court", false, false));
        CenterState.sortForStaff(centers);
        assertEquals("New Court", centers.get(0).getName());
        assertEquals("Hidden Hall", centers.get(1).getName());
        assertEquals("Alpha School", centers.get(2).getName());
        assertEquals("Zeta Gym", centers.get(3).getName());
    }
}
