package com.rutarescue.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Converts an OpenStreetMap Overpass export of road bridges and tunnels into the compact
 * road-structure dataset used by StructureIndex (DOCX section 8: "Prepared bridge/tunnel
 * geometry and status records are versioned separately from the NOAH layers").
 *
 * The Overpass query used (see tools/dataprep/overpass_structures.ql):
 *   way["highway"]["bridge"]["bridge"!="no"](14.35,120.90,14.79,121.14);
 *   way["highway"]["tunnel"]["tunnel"!~"no|building_passage|culvert"](14.35,120.90,14.79,121.14);
 *   out geom tags;
 *
 * Output: structures/road_structures.json and structures/manifest.json.
 * OSM data is (c) OpenStreetMap contributors and licensed under the ODbL.
 *
 * Usage: StructurePrep <overpass json file> <output folder>
 */
public final class StructurePrep {

    private StructurePrep() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: StructurePrep <overpass json> <output folder>");
            System.exit(2);
        }
        Path input = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String osmTimestamp = root.has("osm3s") && root.getAsJsonObject("osm3s").has("timestamp_osm_base")
                ? root.getAsJsonObject("osm3s").get("timestamp_osm_base").getAsString() : "unknown";

        String version = "osm-structures-" + LocalDate.now(ZoneOffset.UTC);
        Path output = outDir.resolve("road_structures.json");
        int bridges = 0, tunnels = 0, skipped = 0;
        long points = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            out.write("{\"version\":\"" + version + "\",\"source\":\"OpenStreetMap contributors (ODbL 1.0) via Overpass API\","
                    + "\"osmTimestamp\":\"" + osmTimestamp + "\",\"structures\":[");
            boolean first = true;
            for (JsonElement element : root.getAsJsonArray("elements")) {
                JsonObject way = element.getAsJsonObject();
                if (!"way".equals(text(way, "type")) || !way.has("geometry")) {
                    skipped++;
                    continue;
                }
                JsonObject tags = way.has("tags") ? way.getAsJsonObject("tags") : new JsonObject();
                String bridge = text(tags, "bridge");
                String tunnel = text(tags, "tunnel");
                String type;
                if (!tunnel.isEmpty() && !"no".equals(tunnel) && !"building_passage".equals(tunnel) && !"culvert".equals(tunnel)) {
                    type = "T";
                    tunnels++;
                } else if (!bridge.isEmpty() && !"no".equals(bridge)) {
                    type = "B";
                    bridges++;
                } else {
                    skipped++;
                    continue;
                }
                JsonArray geometry = way.getAsJsonArray("geometry");
                if (geometry.size() < 2) {
                    skipped++;
                    continue;
                }
                String name = text(tags, "name");
                if (name.isEmpty()) name = text(tags, "bridge:name");
                int layer = 0;
                try {
                    layer = Integer.parseInt(text(tags, "layer").isEmpty() ? "0" : text(tags, "layer").split(";")[0].trim());
                } catch (NumberFormatException ignored) {
                    // Unusual layer values are treated as 0.
                }
                if (!first) out.write(',');
                first = false;
                out.write("\n{\"id\":\"w" + way.get("id").getAsLong() + "\",\"t\":\"" + type + "\",\"n\":"
                        + new Gson().toJson(name) + ",\"l\":" + layer + ",\"h\":" + new Gson().toJson(text(tags, "highway"))
                        + ",\"g\":[");
                for (int i = 0; i < geometry.size(); i++) {
                    JsonObject node = geometry.get(i).getAsJsonObject();
                    if (i > 0) out.write(',');
                    out.write(GeoJsonWriter.format(node.get("lon").getAsDouble(), 6));
                    out.write(',');
                    out.write(GeoJsonWriter.format(node.get("lat").getAsDouble(), 6));
                    points++;
                }
                out.write("]}");
            }
            out.write("\n]}\n");
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("manifestVersion", 1);
        manifest.addProperty("generatedAt", Instant.now().toString());
        manifest.addProperty("datasetVersion", version);
        manifest.addProperty("source", "OpenStreetMap contributors, extracted with the Overpass API (query in tools/dataprep/overpass_structures.ql)");
        manifest.addProperty("license", "Open Database License (ODbL) 1.0 - attribution \"(c) OpenStreetMap contributors\" is required.");
        manifest.addProperty("osmTimestamp", osmTimestamp);
        manifest.addProperty("bridges", bridges);
        manifest.addProperty("tunnels", tunnels);
        manifest.addProperty("points", points);
        manifest.addProperty("skippedElements", skipped);
        manifest.addProperty("statusNote", "All structures start as UNVERIFIED. LGU staff record CLOSED or VERIFIED_OPEN evidence in the app.");
        JsonObject inputInfo = new JsonObject();
        inputInfo.addProperty("file", input.getFileName().toString());
        inputInfo.addProperty("sha256", Hashes.sha256(input));
        manifest.add("input", inputInfo);
        JsonObject outputInfo = new JsonObject();
        outputInfo.addProperty("path", "structures/road_structures.json");
        outputInfo.addProperty("bytes", Files.size(output));
        outputInfo.addProperty("sha256", Hashes.sha256(output));
        manifest.add("output", outputInfo);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (Writer writer = Files.newBufferedWriter(outDir.resolve("manifest.json"), StandardCharsets.UTF_8)) {
            gson.toJson(manifest, writer);
        }
        System.out.println("bridges=" + bridges + " tunnels=" + tunnels + " points=" + points + " skipped=" + skipped);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
