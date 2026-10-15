package com.rutarescue.model;

import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Phase 1 status pills and facility checkboxes, and how older saved values still load. */
public class CenterStatusAndFeatureTest {

    @Test
    public void openAndNearCapacityStillAcceptPeople() {
        assertTrue(CenterStatus.OPEN.isAcceptingEvacuees());
        assertTrue(CenterStatus.NEAR_CAPACITY.isAcceptingEvacuees());
        assertFalse(CenterStatus.FULL.isAcceptingEvacuees());
        assertFalse(CenterStatus.CLOSED.isAcceptingEvacuees());
        assertFalse(CenterStatus.UNAVAILABLE.isAcceptingEvacuees());
    }

    @Test
    public void olderStandbyStatusLoadsAsNotReported() {
        assertEquals(CenterStatus.NOT_REPORTED, CenterStatus.fromName("STANDBY"));
    }

    @Test
    public void olderFeatureNamesMapToTheNewFacilities() {
        assertEquals(AccessibilityFeature.PWD_ACCESSIBLE, AccessibilityFeature.fromName("ACCESSIBLE_ENTRANCE"));
        assertEquals(AccessibilityFeature.PWD_ACCESSIBLE, AccessibilityFeature.fromName("ACCESSIBLE_TOILET"));
        assertEquals(AccessibilityFeature.LACTATION_STATION, AccessibilityFeature.fromName("PRIVATE_AREA"));
        assertNull(AccessibilityFeature.fromName("FAMILY_AREA"));
    }
}
