package com.rutarescue.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks the bundled train map (assets/transit/metro_manila_rail.geojson, built from
 * OpenStreetMap by data/sources/osm-rail-2026-10-19/build_rail_geojson.py): the three running
 * lines, every station named and inside Metro Manila.
 */
public class TransitAssetTest {

    private static JsonObject readAsset() throws IOException {
        File file = new File("src/main/assets/transit/metro_manila_rail.geojson");
        if (!file.exists()) file = new File("app/src/main/assets/transit/metro_manila_rail.geojson");
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    public void hasTheThreeRunningLinesAndTheirStations() throws IOException {
        JsonArray features = readAsset().getAsJsonArray("features");
        Map<String, Integer> stationsPerLine = new HashMap<>();
        int lines = 0;
        for (JsonElement element : features) {
            JsonObject properties = element.getAsJsonObject().getAsJsonObject("properties");
            String kind = properties.get("kind").getAsString();
            String line = properties.get("line").getAsString();
            if (kind.equals("line")) {
                lines++;
                assertTrue(line + " needs a colour", properties.get("colour").getAsString().startsWith("#"));
            } else {
                stationsPerLine.merge(line, 1, Integer::sum);
            }
        }
        assertEquals(3, lines);
        assertEquals(Integer.valueOf(25), stationsPerLine.get("LRT-1")); // Fernando Poe Jr. to Dr. Santos
        assertEquals(Integer.valueOf(13), stationsPerLine.get("LRT-2")); // Recto to Antipolo
        assertEquals(Integer.valueOf(13), stationsPerLine.get("MRT-3")); // North Avenue to Taft Avenue
    }

    @Test
    public void everyStationHasARealNameAndSitsInMetroManila() throws IOException {
        for (JsonElement element : readAsset().getAsJsonArray("features")) {
            JsonObject feature = element.getAsJsonObject();
            JsonObject properties = feature.getAsJsonObject("properties");
            if (!properties.get("kind").getAsString().equals("station")) continue;
            String name = properties.get("name").getAsString();
            assertTrue("Station name too short: '" + name + "'", name.length() >= 2);
            assertFalse(name.contains("/"));
            JsonArray point = feature.getAsJsonObject("geometry").getAsJsonArray("coordinates");
            double lon = point.get(0).getAsDouble();
            double lat = point.get(1).getAsDouble();
            assertTrue(name + " is outside Metro Manila", lon > 120.9 && lon < 121.2 && lat > 14.4 && lat < 14.7);
        }
    }

    @Test
    public void unitedNationsStationIsNamed() throws IOException {
        boolean found = false;
        for (JsonElement element : readAsset().getAsJsonArray("features")) {
            JsonObject properties = element.getAsJsonObject().getAsJsonObject("properties");
            if (properties.get("kind").getAsString().equals("station")
                    && properties.get("name").getAsString().equals("United Nations")) {
                found = true;
            }
        }
        assertTrue("OpenStreetMap names this LRT-1 stop '/'; the build script fixes it", found);
    }
}
