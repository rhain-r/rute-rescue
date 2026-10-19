package com.rutarescue.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * "Nearby" must mean nearby: the closest centers to the resident, never the first ones in
 * alphabetical order, and no made-up "nearest" before the resident's location is known.
 */
public class CenterServiceNearestTest {

    /** The resident in Ermita, Manila. */
    private static final GeoPoint ERMITA = new GeoPoint(14.5826, 120.9845);

    private static EvacuationCenter center(String name, MetroManilaCity city, double lat, double lon,
                                           CenterStatus status) throws ValidationException {
        return new EvacuationCenter("id-" + name, name, name, city, new GeoPoint(lat, lon), status,
                EnumSet.noneOf(AccessibilityFeature.class), VerificationInfo.unverified("test"), true, "");
    }

    private static List<EvacuationCenter> centers() throws ValidationException {
        // Alphabetical order puts the far-away Muntinlupa school first, which is what went wrong.
        return Arrays.asList(
                center("Alabang Elementary School", MetroManilaCity.MUNTINLUPA, 14.4192, 121.0397, CenterStatus.NOT_REPORTED),
                center("Robinsons Manila", MetroManilaCity.MANILA, 14.5782, 120.9845, CenterStatus.NOT_REPORTED),
                center("SM City Manila", MetroManilaCity.MANILA, 14.5899, 120.9837, CenterStatus.NEAR_CAPACITY),
                center("Rizal High School Gym", MetroManilaCity.PASIG, 14.5610, 121.0760, CenterStatus.OPEN));
    }

    @Test
    public void nearestIsTheClosestCenterNotTheFirstByName() throws ValidationException {
        assertEquals("Robinsons Manila", CenterService.nearest(centers(), ERMITA).getName());
    }

    @Test
    public void thereIsNoNearestBeforeTheLocationIsKnown() throws ValidationException {
        assertNull(CenterService.nearest(centers(), null));
    }

    @Test
    public void aFullCenterIsSkippedForTheNearestOne() throws ValidationException {
        List<EvacuationCenter> list = Arrays.asList(
                center("Robinsons Manila", MetroManilaCity.MANILA, 14.5782, 120.9845, CenterStatus.FULL),
                center("SM City Manila", MetroManilaCity.MANILA, 14.5899, 120.9837, CenterStatus.NEAR_CAPACITY));
        assertEquals("SM City Manila", CenterService.nearest(list, ERMITA).getName());
    }

    @Test
    public void nearbyListIsSortedByDistance() throws ValidationException {
        List<EvacuationCenter> nearby = CenterService.filter(centers(), "", false, false, false, ERMITA);
        assertEquals("Robinsons Manila", nearby.get(0).getName());
        assertEquals("SM City Manila", nearby.get(1).getName());
        assertEquals("Alabang Elementary School", nearby.get(nearby.size() - 1).getName());
    }

    @Test
    public void summaryShowsDistanceAndPostedStatus() throws ValidationException {
        EvacuationCenter sm = centers().get(2);
        assertEquals("816 m • Near capacity", CenterService.summary(sm, ERMITA));
    }

    @Test
    public void summaryNamesTheCityWhenNoStatusIsPosted() throws ValidationException {
        EvacuationCenter robinsons = centers().get(1);
        assertEquals("489 m • Manila • no status posted yet", CenterService.summary(robinsons, ERMITA));
        assertEquals("Manila • no status posted yet", CenterService.summary(robinsons, null));
    }
}
