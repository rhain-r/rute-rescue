package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.IndexRange;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Shared test data: a small synthetic "river" of HIGH hazard crossing an east-west road.
 * The grid covers lon 120.999-121.006, lat 14.599-14.601 with 0.0001 degree cells, and
 * only the strip between lon 121.0020 and 121.0030 is HIGH. Nothing here comes from the
 * real NOAH data - it is a controlled scenario for the bridge regression.
 */
final class RiskFixtures {

    static final double MIN_LON = 120.999;
    static final double MIN_LAT = 14.599;
    static final double CELL = 0.0001;
    static final int COLS = 70;
    static final int ROWS = 20;
    static final double ROAD_LAT = 14.6;

    private RiskFixtures() {
    }

    /** HIGH hazard only in the river strip. */
    static HazardGrid riverGrid() {
        return gridWithStrip(HazardClass.HIGH);
    }

    static HazardGrid gridWithStrip(HazardClass stripClass) {
        int[] codes = new int[COLS * ROWS];
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                double lon = MIN_LON + (col + 0.5) * CELL;
                boolean inRiver = lon >= 121.0020 && lon < 121.0030;
                codes[row * COLS + col] = inRiver ? stripClass.getCode() : 0;
            }
        }
        return HazardGrid.fromCodes(FloodScenario.FIVE_YEAR, MIN_LON, MIN_LAT, CELL, COLS, ROWS, codes);
    }

    /** East-west road from lon 121.0000 to 121.0050 (about 540 m) with only two vertices. */
    static RouteCandidate eastWestRoute(int providerIndex, long travelTimeSeconds) {
        return route(providerIndex, travelTimeSeconds, Arrays.asList(
                new GeoPoint(ROAD_LAT, 121.0000), new GeoPoint(ROAD_LAT, 121.0050)), Collections.emptyList());
    }

    /** The same road but drawn with many vertices (to prove vertex density does not matter). */
    static RouteCandidate denseEastWestRoute() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 50; i++) {
            points.add(new GeoPoint(ROAD_LAT, 121.0000 + i * 0.0001));
        }
        return route(0, 600, points, Collections.emptyList());
    }

    static RouteCandidate route(int providerIndex, long travelTimeSeconds, List<GeoPoint> points,
                                List<IndexRange> tunnels) {
        return new RouteCandidate(providerIndex, points, 540, travelTimeSeconds, 0, false,
                tunnels, Collections.emptyList(), Collections.emptyList());
    }

    /** A bridge carrying the road over the river strip. */
    static RoadStructureRecord bridgeOverRiver() {
        return new RoadStructureRecord("w-test-bridge", RoadStructure.BRIDGE, "Test River Bridge", 1,
                Arrays.asList(new GeoPoint(ROAD_LAT, 121.0018), new GeoPoint(ROAD_LAT, 121.0032)));
    }

    static StructureIndex index(RoadStructureRecord... records) {
        return new StructureIndex(Arrays.asList(records), "test");
    }
}
