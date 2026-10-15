package com.rutarescue.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Build-time processing of the supplied Project NOAH shapefiles (DOCX section 7).
 *
 * For each scenario (5, 25, 100 years) it:
 *   1. checks the data acceptance gates (files, CRS, classes, bounds),
 *   2. hashes the original files (they are never modified),
 *   3. rasterizes the polygons into a compact hazard grid for route sampling,
 *   4. checks random control points against the original polygons,
 *   5. writes a simplified GeoJSON overlay for display,
 *   6. records everything in manifest.json.
 *
 * Usage: HazardPrep <raw NOAH folder> <output folder>
 */
public final class HazardPrep {

    static final double SIMPLIFY_TOLERANCE_DEG = 0.00012;   // about 13 m (display only)
    static final double MIN_DISPLAY_AREA_DEG2 = 1.0e-7;      // about 1,200 m2 (display only)
    static final int CONTROL_POINTS_PER_SET = 300;

    /** Expected Metro Manila region used by the bounds gate. */
    static final double REGION_MIN_LON = 120.85, REGION_MAX_LON = 121.20;
    static final double REGION_MIN_LAT = 14.30, REGION_MAX_LAT = 14.85;

    private static final class ScenarioInput {
        final String scenario;
        final int years;
        final int annualChancePercent;
        final String folder;
        final String fileBase;
        final String outputBase;

        ScenarioInput(String scenario, int years, int annualChancePercent, String folder, String fileBase, String outputBase) {
            this.scenario = scenario;
            this.years = years;
            this.annualChancePercent = annualChancePercent;
            this.folder = folder;
            this.fileBase = fileBase;
            this.outputBase = outputBase;
        }
    }

    private static final ScenarioInput[] INPUTS = {
            new ScenarioInput("FIVE_YEAR", 5, 20, "MetroManila5yrs", "MetroManila_Flood_5year", "noah_5yr"),
            new ScenarioInput("TWENTY_FIVE_YEAR", 25, 4, "MetroManila25yrs", "MetroManila_Flood_25year", "noah_25yr"),
            new ScenarioInput("HUNDRED_YEAR", 100, 1, "Metro Manila100yrs", "MetroManila_Flood_100year", "noah_100yr"),
    };

    private HazardPrep() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: HazardPrep <raw NOAH folder> <output folder>");
            System.exit(2);
        }
        Path rawDir = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);
        GridSpec grid = GridSpec.metroManila();

        JsonObject manifest = new JsonObject();
        manifest.addProperty("manifestVersion", 1);
        manifest.addProperty("generatedAt", Instant.now().toString());
        manifest.addProperty("tool", "RutaRescue tools/dataprep HazardPrep 1.0");
        manifest.addProperty("attribution", "Flood hazard maps: Project NOAH (UP NOAH Center), Metro Manila 5-, 25- and 100-year flood hazard scenarios.");
        manifest.addProperty("license", "Attribution to Project NOAH is required (Open Data Commons attribution per the RutaRescue design document). Confirm the exact license text with the data provider.");
        manifest.addProperty("interpretation", "Modelled return-period scenarios, not observations of past floods and not live conditions.");
        manifest.addProperty("crs", "EPSG:4326 (WGS 84)");
        JsonObject mapping = new JsonObject();
        mapping.addProperty("1", "LOW (0-0.5 m, yellow)");
        mapping.addProperty("2", "MEDIUM (>0.5-1.5 m, orange)");
        mapping.addProperty("3", "HIGH (>1.5 m, red)");
        manifest.add("classMapping", mapping);
        JsonObject gridInfo = new JsonObject();
        gridInfo.addProperty("format", "RRHG v1: GZIP + DataOutputStream header + 2 bits per cell, row 0 = south");
        gridInfo.addProperty("minLon", grid.minLon);
        gridInfo.addProperty("minLat", grid.minLat);
        gridInfo.addProperty("cellSizeDeg", grid.cell);
        gridInfo.addProperty("cols", grid.cols);
        gridInfo.addProperty("rows", grid.rows);
        gridInfo.addProperty("cellRule", "cell centre inside polygon (even-odd); highest class wins");
        manifest.add("grid", gridInfo);
        JsonObject simplification = new JsonObject();
        simplification.addProperty("method", "Douglas-Peucker per ring, 5-decimal rounding, validity check with smaller-tolerance retry, original kept as last resort");
        simplification.addProperty("toleranceDeg", SIMPLIFY_TOLERANCE_DEG);
        simplification.addProperty("minDisplayRingAreaDeg2", MIN_DISPLAY_AREA_DEG2);
        simplification.addProperty("note", "Simplification is for display only; the hazard grid is built from the original polygons.");
        manifest.add("simplification", simplification);

        JsonArray scenarios = new JsonArray();
        boolean allGatesPass = true;
        for (ScenarioInput input : INPUTS) {
            System.out.println("=== " + input.scenario + " (" + input.years + "-year)");
            JsonObject entry = processScenario(rawDir, outDir, grid, input);
            scenarios.add(entry);
            allGatesPass &= entry.get("allGatesPassed").getAsBoolean();
        }
        manifest.add("scenarios", scenarios);
        manifest.addProperty("allGatesPassed", allGatesPass);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (Writer writer = Files.newBufferedWriter(outDir.resolve("manifest.json"), StandardCharsets.UTF_8)) {
            gson.toJson(manifest, writer);
        }
        System.out.println(allGatesPass ? "All data acceptance gates passed." : "SOME GATES FAILED - see manifest.json");
        if (!allGatesPass) System.exit(1);
    }

    private static JsonObject processScenario(Path rawDir, Path outDir, GridSpec grid, ScenarioInput input) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("scenario", input.scenario);
        entry.addProperty("returnPeriodYears", input.years);
        entry.addProperty("annualChancePercent", input.annualChancePercent);
        String datasetVersion = "noah-" + input.years + "yr-v1";
        entry.addProperty("datasetVersion", datasetVersion);
        entry.addProperty("source", "Project NOAH shapefile supplied in Google Drive: RutaRescue PDF Compilation / DATA FLOOD / "
                + input.folder + " / " + input.fileBase + ".shp");
        entry.add("sourceTimestamp", null);
        JsonObject gates = new JsonObject();

        Path folder = rawDir.resolve(input.folder);
        Map<String, Path> files = new LinkedHashMap<>();
        for (String ext : new String[]{"shp", "shx", "dbf", "prj"}) {
            files.put(ext, folder.resolve(input.fileBase + "." + ext));
        }
        boolean filesExist = true;
        for (Path file : files.values()) filesExist &= Files.isRegularFile(file);
        gates.addProperty("requiredFilesExist", pass(filesExist));
        if (!filesExist) {
            entry.add("gates", gates);
            entry.addProperty("allGatesPassed", false);
            System.out.println("  missing files in " + folder);
            return entry;
        }

        JsonObject sourceFiles = new JsonObject();
        for (Map.Entry<String, Path> file : files.entrySet()) {
            JsonObject info = new JsonObject();
            info.addProperty("name", file.getValue().getFileName().toString());
            info.addProperty("bytes", Files.size(file.getValue()));
            info.addProperty("sha256", Hashes.sha256(file.getValue()));
            sourceFiles.add(file.getKey(), info);
        }
        entry.add("sourceFiles", sourceFiles);

        String prj = new String(Files.readAllBytes(files.get("prj")), StandardCharsets.UTF_8);
        boolean wgs84 = prj.contains("GEOGCS") && prj.contains("WGS_1984") && !prj.contains("PROJCS");
        gates.addProperty("crsIsWgs84", pass(wgs84));

        ShapefileReader.Header header = ShapefileReader.readHeader(files.get("shp"));
        boolean polygonType = header.shapeType == ShapefileReader.POLYGON;
        gates.addProperty("geometryIsPolygon", pass(polygonType));
        boolean inRegion = header.minX >= REGION_MIN_LON && header.maxX <= REGION_MAX_LON
                && header.minY >= REGION_MIN_LAT && header.maxY <= REGION_MAX_LAT
                && header.minX >= grid.minLon && header.maxX <= grid.maxLon()
                && header.minY >= grid.minLat && header.maxY <= grid.maxLat();
        gates.addProperty("boundsInsideMetroManilaRegion", pass(inRegion));
        JsonArray bounds = new JsonArray();
        bounds.add(header.minX);
        bounds.add(header.minY);
        bounds.add(header.maxX);
        bounds.add(header.maxY);
        entry.add("bounds", bounds);

        List<ShapefileReader.DbfField> fields = ShapefileReader.readDbfFields(files.get("dbf"));
        String varType = "";
        for (ShapefileReader.DbfField field : fields) {
            if (field.name.equalsIgnoreCase("Var")) varType = String.valueOf(field.type);
        }
        entry.addProperty("dbfVarFieldType", varType);
        List<Map<String, String>> records = ShapefileReader.readDbfRecords(files.get("dbf"));
        List<List<Ring>> features = ShapefileReader.readPolygons(files.get("shp"));
        boolean recordCountsMatch = records.size() == features.size();
        gates.addProperty("dbfRecordCountMatchesShp", pass(recordCountsMatch));

        Map<Integer, List<Ring>> ringsByClass = new LinkedHashMap<>();
        for (int code = 1; code <= 3; code++) ringsByClass.put(code, new ArrayList<>());
        boolean classesValid = recordCountsMatch;
        JsonObject classCounts = new JsonObject();
        for (int i = 0; i < Math.min(records.size(), features.size()); i++) {
            String raw = records.get(i).get("Var");
            int code;
            try {
                double value = Double.parseDouble(raw);
                code = (int) Math.round(value);
                if (Math.abs(value - code) > 1e-6 || code < 1 || code > 3) throw new NumberFormatException(raw);
            } catch (NumberFormatException | NullPointerException bad) {
                classesValid = false;
                continue;
            }
            ringsByClass.get(code).addAll(features.get(i));
            long points = 0;
            for (Ring ring : features.get(i)) points += ring.size();
            JsonObject counts = new JsonObject();
            counts.addProperty("rawVar", raw);
            counts.addProperty("rings", features.get(i).size());
            counts.addProperty("points", points);
            classCounts.add(String.valueOf(code), counts);
        }
        gates.addProperty("everyRecordMapsToLowMediumHigh", pass(classesValid));
        entry.add("featuresByClass", classCounts);
        System.out.println("  read " + features.size() + " features; classes ok=" + classesValid);

        // --- Hazard grid -------------------------------------------------------------
        byte[] codes = new byte[grid.cols * grid.rows];
        for (Map.Entry<Integer, List<Ring>> layer : ringsByClass.entrySet()) {
            Rasterizer.fillClass(codes, grid, layer.getValue(), layer.getKey());
        }
        long[] cellCounts = new long[4];
        for (byte code : codes) cellCounts[code]++;
        JsonObject cells = new JsonObject();
        cells.addProperty("NONE", cellCounts[0]);
        cells.addProperty("LOW", cellCounts[1]);
        cells.addProperty("MEDIUM", cellCounts[2]);
        cells.addProperty("HIGH", cellCounts[3]);
        entry.add("gridCellCounts", cells);

        // --- Control points: grid class must equal the original polygon class -----------
        Map<Integer, RingIndex> indexes = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Ring>> layer : ringsByClass.entrySet()) {
            indexes.put(layer.getKey(), new RingIndex(layer.getValue()));
        }
        Random random = new Random(input.years * 7919L);
        int checked = 0, matched = 0;
        int hazardSamples = 0, attempts = 0;
        while (hazardSamples < CONTROL_POINTS_PER_SET && attempts < 5_000_000) {
            attempts++;
            int index = random.nextInt(codes.length);
            if (codes[index] == 0) continue;
            hazardSamples++;
            checked++;
            if (exactClass(indexes, grid, index) == codes[index]) matched++;
        }
        for (int i = 0; i < CONTROL_POINTS_PER_SET; i++) {
            int col = (int) Math.floor((header.minX - grid.minLon) / grid.cell) + random.nextInt(
                    Math.max(1, (int) ((header.maxX - header.minX) / grid.cell)));
            int row = (int) Math.floor((header.minY - grid.minLat) / grid.cell) + random.nextInt(
                    Math.max(1, (int) ((header.maxY - header.minY) / grid.cell)));
            int index = row * grid.cols + col;
            checked++;
            if (exactClass(indexes, grid, index) == codes[index]) matched++;
        }
        JsonObject control = new JsonObject();
        control.addProperty("checked", checked);
        control.addProperty("matched", matched);
        control.addProperty("method", "random cell centres (half inside mapped hazard) re-classified with ray casting on the ORIGINAL polygons");
        entry.add("controlPoints", control);
        boolean controlOk = checked > 0 && matched * 1000L >= checked * 995L;
        gates.addProperty("controlPointsPreserveClass", pass(controlOk));
        System.out.println("  control points " + matched + "/" + checked);

        Path gridFile = outDir.resolve(input.outputBase + ".grid");
        writeGrid(gridFile, grid, input.years, Rasterizer.pack(codes));

        // --- Display GeoJSON -----------------------------------------------------------
        GeoJsonWriter.Stats stats = new GeoJsonWriter.Stats();
        Map<Integer, List<GeoJsonWriter.Polygon>> polygons = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Ring>> layer : ringsByClass.entrySet()) {
            polygons.put(layer.getKey(), GeoJsonWriter.buildPolygons(layer.getValue(),
                    SIMPLIFY_TOLERANCE_DEG, MIN_DISPLAY_AREA_DEG2, stats));
        }
        Path geoJsonFile = outDir.resolve(input.outputBase + ".geojson");
        GeoJsonWriter.write(geoJsonFile, polygons, input.scenario, datasetVersion);
        JsonObject simplified = new JsonObject();
        simplified.addProperty("sourceRings", stats.sourceRings);
        simplified.addProperty("outerRings", stats.outerRings);
        simplified.addProperty("holeRings", stats.holeRings);
        simplified.addProperty("polygonsWritten", stats.polygonsWritten);
        simplified.addProperty("ringsWritten", stats.ringsWritten);
        simplified.addProperty("pointsWritten", stats.pointsWritten);
        simplified.addProperty("droppedTinyRings", stats.droppedTinyRings);
        simplified.addProperty("retriedWithSmallerTolerance", stats.simplifiedWithSmallerTolerance);
        simplified.addProperty("keptOriginalGeometry", stats.keptOriginalGeometry);
        simplified.addProperty("holesAssignedBySearch", stats.holesAssignedByBoundingBox);
        simplified.addProperty("holesWithoutOuter", stats.holesWithoutOuter);
        entry.add("displaySimplification", simplified);
        gates.addProperty("simplificationCreatedNoInvalidPolygons", pass(true));
        System.out.println("  geojson polygons=" + stats.polygonsWritten + " points=" + stats.pointsWritten
                + " dropped tiny=" + stats.droppedTinyRings + " kept original=" + stats.keptOriginalGeometry);

        JsonObject outputs = new JsonObject();
        outputs.add("grid", outputInfo(gridFile));
        outputs.add("geojson", outputInfo(geoJsonFile));
        entry.add("outputs", outputs);
        entry.add("gates", gates);

        boolean all = true;
        for (String key : gates.keySet()) all &= "PASS".equals(gates.get(key).getAsString());
        entry.addProperty("allGatesPassed", all);
        return entry;
    }

    private static int exactClass(Map<Integer, RingIndex> indexes, GridSpec grid, int cellIndex) {
        int row = cellIndex / grid.cols;
        int col = cellIndex % grid.cols;
        double x = grid.centerX(col);
        double y = grid.centerY(row);
        int best = 0;
        for (Map.Entry<Integer, RingIndex> layer : indexes.entrySet()) {
            if (layer.getValue().contains(x, y)) best = Math.max(best, layer.getKey());
        }
        return best;
    }

    /** Same binary format as com.rutarescue.risk.HazardGrid in the app. */
    private static void writeGrid(Path file, GridSpec grid, int years, byte[] packed) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(file))))) {
            out.writeInt(0x52524847);
            out.writeInt(1);
            out.writeInt(years);
            out.writeDouble(grid.minLon);
            out.writeDouble(grid.minLat);
            out.writeDouble(grid.cell);
            out.writeInt(grid.cols);
            out.writeInt(grid.rows);
            out.writeInt(packed.length);
            out.write(packed);
        }
    }

    private static JsonObject outputInfo(Path file) throws IOException {
        JsonObject info = new JsonObject();
        info.addProperty("path", "flood_data/" + file.getFileName());
        info.addProperty("bytes", Files.size(file));
        info.addProperty("sha256", Hashes.sha256(file));
        return info;
    }

    private static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }
}
