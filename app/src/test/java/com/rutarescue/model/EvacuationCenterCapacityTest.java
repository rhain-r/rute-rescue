package com.rutarescue.model;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterCapacity;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The capacity screen saves headcount, status, facilities and who changed it, in one step. */
public class EvacuationCenterCapacityTest {

    private static EvacuationCenter almario() throws ValidationException {
        return new EvacuationCenter("c1", "Almario Elementary School", "Barangay 20, Tondo", MetroManilaCity.MANILA,
                new GeoPoint(14.6150, 120.9700), CenterStatus.NOT_REPORTED, EnumSet.noneOf(AccessibilityFeature.class),
                VerificationInfo.unverified("test"), false, "");
    }

    @Test
    public void aNewCenterHasNoCapacityAndNoChangeYet() throws ValidationException {
        EvacuationCenter center = almario();
        assertFalse(center.getCapacity().isSet());
        assertNull(center.getLastCapacityChange());
        assertFalse(center.isLocationVerified());
    }

    @Test
    public void updateCapacityStoresEverythingAndRecordsTheChange() throws ValidationException {
        EvacuationCenter center = almario();
        center.updateCapacity(new CenterCapacity(300, 207), CenterStatus.OPEN,
                EnumSet.noneOf(AccessibilityFeature.class), "uid-1", "Manila DRRM Demo", "setup", 1_000L);
        center.updateCapacity(new CenterCapacity(300, 212), CenterStatus.NEAR_CAPACITY,
                EnumSet.of(AccessibilityFeature.PWD_ACCESSIBLE, AccessibilityFeature.LACTATION_STATION),
                "uid-1", "Manila DRRM Demo", "barangay field check", 2_000L);

        assertEquals(212, center.getCapacity().getCurrentOccupancy());
        assertEquals(CenterStatus.NEAR_CAPACITY, center.getStatus());
        assertTrue(center.getFeatures().contains(AccessibilityFeature.LACTATION_STATION));
        assertEquals("+5 by Manila DRRM Demo", center.getLastCapacityChange().describe());
        assertEquals("barangay field check", center.getLastCapacityChange().getSource());
        assertEquals(2_000L, center.getAuditInfo().getUpdatedAt());
    }

    @Test
    public void locationDetailsAreTrimmedAndOptional() throws ValidationException {
        EvacuationCenter center = almario();
        center.setLocationDetails("  Barangay 20 ", "  beside Brgy. 20 hall ", true);
        assertEquals("Barangay 20", center.getBarangay());
        assertEquals("beside Brgy. 20 hall", center.getLandmark());
        assertTrue(center.isLocationVerified());
        center.setLocationDetails(null, null, false);
        assertEquals("", center.getLandmark());
    }
}
