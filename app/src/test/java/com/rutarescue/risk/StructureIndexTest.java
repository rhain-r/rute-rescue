package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class StructureIndexTest {

    /** A north-south flyover above an east-west road. */
    private static RoadStructureRecord flyover() {
        return new RoadStructureRecord("w1", RoadStructure.BRIDGE, "Test Flyover", 1,
                Arrays.asList(new GeoPoint(14.5990, 121.0025), new GeoPoint(14.6010, 121.0025)));
    }

    private final StructureIndex index = new StructureIndex(Arrays.asList(flyover()), "test");

    @Test
    public void travellingAlongTheFlyoverIsBridge() {
        assertEquals(RoadStructure.BRIDGE, index.match(new GeoPoint(14.6000, 121.0025), 0.0).getType());
        // Driving the other way along it is still the same bridge.
        assertEquals(RoadStructure.BRIDGE, index.match(new GeoPoint(14.6000, 121.0025), 180.0).getType());
    }

    @Test
    public void roadPassingUnderTheFlyoverStaysSurface() {
        assertEquals(RoadStructure.SURFACE, index.match(new GeoPoint(14.6000, 121.0025), 90.0).getType());
    }

    @Test
    public void ambiguousAngleIsUnknownNotSurface() {
        assertEquals(RoadStructure.UNKNOWN, index.match(new GeoPoint(14.6000, 121.0025), 45.0).getType());
    }

    @Test
    public void farAwayIsSurface() {
        assertEquals(RoadStructure.SURFACE, index.match(new GeoPoint(14.6000, 121.0100), 0.0).getType());
    }

    @Test
    public void nearestFindsTappedStructure() {
        assertSame(index.findById("w1"), index.nearest(new GeoPoint(14.6000, 121.00255), 30));
    }
}
