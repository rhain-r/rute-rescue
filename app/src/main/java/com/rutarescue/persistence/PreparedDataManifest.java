package com.rutarescue.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.rutarescue.exception.MalformedDataException;
import com.rutarescue.model.hazard.FloodScenario;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads assets/flood_data/manifest.json written by the data preparation tool. The
 * manifest records the source, attribution, hashes and version of every prepared NOAH
 * layer (DOCX section 7), which the app shows as the "Prepared" data stamp.
 */
public final class PreparedDataManifest {

    /** What the app needs to know about one prepared scenario. */
    public static final class ScenarioInfo {
        private final FloodScenario scenario;
        private final String datasetVersion;
        private final String source;
        private final String processedAt;
        private final String gridSha256;
        private final String geoJsonSha256;
        private final String sourceShpSha256;

        ScenarioInfo(FloodScenario scenario, String datasetVersion, String source, String processedAt,
                     String gridSha256, String geoJsonSha256, String sourceShpSha256) {
            this.scenario = scenario;
            this.datasetVersion = datasetVersion;
            this.source = source;
            this.processedAt = processedAt;
            this.gridSha256 = gridSha256;
            this.geoJsonSha256 = geoJsonSha256;
            this.sourceShpSha256 = sourceShpSha256;
        }

        public FloodScenario getScenario() { return scenario; }
        public String getDatasetVersion() { return datasetVersion; }
        public String getSource() { return source; }
        public String getProcessedAt() { return processedAt; }
        public String getGridSha256() { return gridSha256; }
        public String getGeoJsonSha256() { return geoJsonSha256; }
        public String getSourceShpSha256() { return sourceShpSha256; }
    }

    private final String attribution;
    private final String license;
    private final String generatedAt;
    private final Map<FloodScenario, ScenarioInfo> scenarios;

    private PreparedDataManifest(String attribution, String license, String generatedAt,
                                 Map<FloodScenario, ScenarioInfo> scenarios) {
        this.attribution = attribution;
        this.license = license;
        this.generatedAt = generatedAt;
        this.scenarios = Collections.unmodifiableMap(scenarios);
    }

    public static PreparedDataManifest read(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<FloodScenario, ScenarioInfo> scenarios = new EnumMap<>(FloodScenario.class);
            JsonArray list = root.getAsJsonArray("scenarios");
            if (list != null) {
                for (JsonElement element : list) {
                    JsonObject item = element.getAsJsonObject();
                    FloodScenario scenario = FloodScenario.fromName(text(item, "scenario"));
                    JsonObject outputs = item.has("outputs") ? item.getAsJsonObject("outputs") : new JsonObject();
                    JsonObject sources = item.has("sourceFiles") ? item.getAsJsonObject("sourceFiles") : new JsonObject();
                    scenarios.put(scenario, new ScenarioInfo(scenario,
                            text(item, "datasetVersion"),
                            text(item, "source"),
                            text(root, "generatedAt"),
                            nestedText(outputs, "grid", "sha256"),
                            nestedText(outputs, "geojson", "sha256"),
                            nestedText(sources, "shp", "sha256")));
                }
            }
            return new PreparedDataManifest(text(root, "attribution"), text(root, "license"),
                    text(root, "generatedAt"), scenarios);
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new MalformedDataException("manifest.json is not valid: " + e.getMessage(), e);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String nestedText(JsonObject object, String child, String key) {
        JsonElement value = object.get(child);
        return value != null && value.isJsonObject() ? text(value.getAsJsonObject(), key) : "";
    }

    /** Empty manifest used when the asset is missing, so screens can still say "unknown". */
    public static PreparedDataManifest empty() {
        return new PreparedDataManifest("Project NOAH flood hazard maps", "", "",
                new EnumMap<>(FloodScenario.class));
    }

    public ScenarioInfo get(FloodScenario scenario) {
        return scenarios.get(scenario);
    }

    public String versionOf(FloodScenario scenario) {
        ScenarioInfo info = scenarios.get(scenario);
        return info == null || info.getDatasetVersion().isEmpty() ? "unversioned" : info.getDatasetVersion();
    }

    public String getAttribution() { return attribution; }
    public String getLicense() { return license; }
    public String getGeneratedAt() { return generatedAt; }
    public Map<FloodScenario, ScenarioInfo> getScenarios() { return scenarios; }
}
