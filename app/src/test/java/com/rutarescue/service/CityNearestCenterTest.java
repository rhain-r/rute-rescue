package com.rutarescue.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.persistence.BundledCenterReader;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every one of the 17 cities gets its own "nearest" center when the phone's location is not
 * known yet: it is measured from that city's hall (MetroManilaCity.getHallLocation), using the
 * real bundled PLM SSC center list. Prints, per city, how far that nearest center is.
 */
public class CityNearestCenterTest {

    private static List<EvacuationCenter> bundled;

    @BeforeClass
    public static void readBundledCenters() throws IOException {
        File file = new File("src/main/assets/" + BundledCenterReader.ASSET);
        if (!file.exists()) file = new File("app/src/main/assets/" + BundledCenterReader.ASSET);
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            bundled = new BundledCenterReader().read(reader);
        }
    }

    @Test
    public void everyCityHallIsInsideMetroManilaAndInADifferentPlace() {
        Set<String> places = new HashSet<>();
        for (MetroManilaCity city : MetroManilaCity.values()) {
            GeoPoint hall = city.getHallLocation();
            assertTrue(city + " hall is outside Metro Manila", hall.getLatitude() > 14.35 && hall.getLatitude() < 14.80
                    && hall.getLongitude() > 120.90 && hall.getLongitude() < 121.15);
            assertTrue(city + " shares a hall location", places.add(hall.toString()));
        }
    }

    @Test
    public void aMandaluyongResidentWithoutGpsGetsTheMandaluyongCenter() {
        EvacuationCenter nearest = CenterService.nearest(bundled, MetroManilaCity.MANDALUYONG.getHallLocation());
        assertNotNull(nearest);
        assertEquals(MetroManilaCity.MANDALUYONG, nearest.getCity());
        assertTrue(CenterService.distance(nearest, MetroManilaCity.MANDALUYONG.getHallLocation()) < 2_000);
    }

    @Test
    public void everyCityWithBundledCentersGetsOneOfItsOwn() {
        StringBuilder report = new StringBuilder("Nearest bundled center to each city hall:\n");
        for (MetroManilaCity city : MetroManilaCity.values()) {
            GeoPoint hall = city.getHallLocation();
            EvacuationCenter nearest = CenterService.nearest(bundled, hall);
            assertNotNull(city + " has no center at all", nearest);
            boolean cityHasCenters = false;
            for (EvacuationCenter center : bundled) {
                if (center.getCity() == city) cityHasCenters = true;
            }
            double km = CenterService.distance(nearest, hall) / 1000.0;
            report.append(String.format("  %-12s %5.1f km  %s (%s)%s%n", city, km, nearest.getName(),
                    nearest.getCity().getDisplayName(), cityHasCenters ? "" : "  <- no bundled center in this city"));
            if (cityHasCenters) {
                // A city with its own centers must not be sent across Metro Manila.
                assertTrue(city + ": nearest is " + km + " km away", km < 6);
            }
        }
        System.out.print(report);
    }
}
