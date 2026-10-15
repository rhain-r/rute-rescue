package com.rutarescue.risk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DOCX acceptance journey 4 on the REAL prepared data shipped in the app
 * (assets/flood_data/noah_5yr.grid and assets/structures/road_structures.json):
 * a walk across MacArthur Bridge over the Pasig River in Manila, where the 5-year NOAH
 * scenario maps HIGH hazard on the river below the bridge.
 *
 *   - as a plain surface road the crossing is RED (it follows the ground hazard);
 *   - on the unverified bridge it is NOT given the river's HIGH class, but it is flagged as
 *     an unverified structure (AMBER), never silently green;
 *   - when the LGU marks the bridge closed the route becomes unavailable;
 *   - among three alternatives the bridge route is ranked first, ahead of two surface routes
 *     that cross HIGH ground.
 *
 * The same three routes are used for the on-device check (a cached TomTom-format answer).
 */
public class RealDataBridgeTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final String MACARTHUR = "MacArthur Bridge";

    /** Route A: south bank -> MacArthur Bridge (northbound) -> Santa Cruz. */
    private static final double[][] VIA_BRIDGE = {
            {14.5948, 120.98010}, {14.596014, 120.980314}, {14.597162, 120.980511},
            {14.597506, 120.98057}, {14.5990, 120.98080}};
    /** Route B: crosses the river west of the bridge where no bridge is mapped. */
    private static final double[][] WEST_CROSSING = {
            {14.5948, 120.98010}, {14.5960, 120.9790}, {14.5980, 120.9788}, {14.5990, 120.98080}};
    /** Route C: a longer crossing further east, also without a mapped bridge. */
    private static final double[][] EAST_CROSSING = {
            {14.5948, 120.98010}, {14.5950, 120.9840}, {14.5985, 120.9845}, {14.5990, 120.98080}};

    private static HazardGrid grid;
    private final RiskEngine engine = new RiskEngine();

    @BeforeClass
    public static void loadGrid() throws IOException {
        try (InputStream in = new FileInputStream(asset("flood_data/noah_5yr.grid"))) {
            grid = HazardGrid.read(in, FloodScenario.FIVE_YEAR, "noah-5yr-v1");
        }
    }

    @Test
    public void riverBelowTheBridgeIsHighInTheRealGrid() {
        // Middle of the MacArthur Bridge span: the ground below is HIGH in the 5-year scenario.
        assertEquals(HazardClass.HIGH, grid.classify(14.5966, 120.98042));
    }

    @Test
    public void surfaceCrossingFollowsTheGroundHazard() throws IOException {
        RouteAssessment result = assess(VIA_BRIDGE, StructureLookup.NONE);
        assertEquals(RiskLevel.RED, result.getMaxLevel());
        assertEquals(HazardClass.HIGH, result.getMaxEffectiveHazard());
    }

    @Test
    public void unverifiedBridgeIsNotGivenTheRiverHazardButIsFlagged() throws IOException {
        RouteAssessment result = assess(VIA_BRIDGE, index(null));
        assertNotEquals("the bridge must not inherit the river's HIGH class",
                HazardClass.HIGH, result.getMaxEffectiveHazard());
        assertEquals(RiskLevel.AMBER, result.getMaxLevel());
        assertTrue("bridge distance must be counted as unverified", result.getUnverifiedStructureMeters() > 50);
        assertFalse(result.isClosed());
    }

    @Test
    public void closedBridgeMakesTheRouteUnavailable() throws IOException {
        RouteAssessment result = assess(VIA_BRIDGE, index(StructureStatus.CLOSED));
        assertTrue(result.isClosed());
        assertEquals(RiskLevel.UNAVAILABLE, result.getMaxLevel());
    }

    @Test
    public void bridgeRouteIsRankedSaferAmongTheAlternatives() throws IOException {
        StructureIndex structures = index(null);
        RiskContext context = new RiskContext(grid, structures, ClosureLookup.NONE, NOW);
        List<RouteAssessment> ranked = new RouteRanker().rank(Arrays.asList(
                engine.assess(candidate(0, WEST_CROSSING), context),
                engine.assess(candidate(1, EAST_CROSSING), context),
                engine.assess(candidate(2, VIA_BRIDGE), context)));
        assertEquals("the bridge route (provider index 2) ranks first", 2, ranked.get(0).getCandidate().getProviderIndex());
        assertEquals(HazardClass.HIGH, ranked.get(1).getMaxEffectiveHazard());
        assertEquals(HazardClass.HIGH, ranked.get(2).getMaxEffectiveHazard());
    }

    /** Northbound across Jones Bridge, whose roadway OSM tags as "Padre Burgos Avenue" (w727174384). */
    private static final double[][] JONES_NORTHBOUND = {
            {14.5947, 120.9780}, {14.59526, 120.977687}, {14.595334, 120.977645},
            {14.596264, 120.977105}, {14.596369, 120.977044}, {14.5970, 120.9767}};

    @Test
    public void closingOneWayOfJonesBridgeClosesBothDirections() throws IOException {
        StructureIndex index = index(null);
        // The LGU tapped the SOUTHBOUND roadway (this happened during the on-device test).
        RoadStructureRecord southbound = index.findById("w1044355499");
        List<RoadStructureRecord> parts = index.partsOfSameStructure(southbound, 25, 20);
        boolean northboundIncluded = false;
        for (RoadStructureRecord part : parts) {
            if ("w727174384".equals(part.getId())) northboundIncluded = true;
            part.applyStatus(StructureStatus.CLOSED, "Field check (test)", NOW, "lgu-test");
        }
        assertTrue("the opposite carriageway is part of the same bridge", northboundIncluded);
        RouteAssessment northbound = engine.assess(candidate(0, JONES_NORTHBOUND),
                new RiskContext(grid, index, ClosureLookup.NONE, NOW));
        assertTrue("a northbound route over the closed bridge is unavailable", northbound.isClosed());
    }

    // ------------------------------------------------------------------ helpers

    private RouteAssessment assess(double[][] route, StructureLookup structures) {
        return engine.assess(candidate(0, route), new RiskContext(grid, structures, ClosureLookup.NONE, NOW));
    }

    private static RouteCandidate candidate(int index, double[][] route) {
        List<GeoPoint> points = new ArrayList<>();
        for (double[] point : route) points.add(new GeoPoint(point[0], point[1]));
        return new RouteCandidate(index, points, 500, 400, 0, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /** Builds the real structure index; optionally gives MacArthur Bridge an LGU status. */
    private static StructureIndex index(StructureStatus macArthurStatus) throws IOException {
        List<RoadStructureRecord> records = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(asset("structures/road_structures.json")),
                StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("structures")) {
                JsonObject item = element.getAsJsonObject();
                List<GeoPoint> geometry = new ArrayList<>();
                JsonArray flat = item.getAsJsonArray("g");
                for (int i = 0; i + 1 < flat.size(); i += 2) {
                    geometry.add(new GeoPoint(flat.get(i + 1).getAsDouble(), flat.get(i).getAsDouble()));
                }
                RoadStructure type = "T".equals(item.get("t").getAsString()) ? RoadStructure.TUNNEL : RoadStructure.BRIDGE;
                RoadStructureRecord record = new RoadStructureRecord(item.get("id").getAsString(), type,
                        item.get("n").getAsString(), item.get("l").getAsInt(), geometry);
                if (macArthurStatus != null && MACARTHUR.equals(record.getName())) {
                    record.applyStatus(macArthurStatus, "Field inspection (test)", NOW, "lgu-test");
                }
                records.add(record);
            }
        }
        return new StructureIndex(records, "osm-structures-test");
    }

    /** Unit tests run with the app module as the working directory. */
    private static File asset(String path) {
        File file = new File("src/main/assets/" + path);
        if (!file.exists()) file = new File("app/src/main/assets/" + path);
        return file;
    }
}
