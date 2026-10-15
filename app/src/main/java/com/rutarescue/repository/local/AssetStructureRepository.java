package com.rutarescue.repository.local;

import android.content.res.AssetManager;

import com.google.gson.stream.JsonReader;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.structure.RoadStructure;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.repository.StructureStatusRepository;
import com.rutarescue.risk.StructureIndex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reads the prepared bridge/tunnel dataset (assets/structures/road_structures.json,
 * built from OpenStreetMap by tools/dataprep) with a streaming JSON reader, and builds a
 * StructureIndex with the latest LGU statuses applied.
 */
public final class AssetStructureRepository {

    private final AssetManager assets;
    private List<RoadStructureRecord> baseRecords;
    private String version = "";
    private String source = "";

    public AssetStructureRepository(AssetManager assets) {
        this.assets = assets;
    }

    /** Parses the dataset once (call from a background thread). */
    public synchronized List<RoadStructureRecord> loadBaseBlocking() throws IOException {
        if (baseRecords != null) return baseRecords;
        List<RoadStructureRecord> records = new ArrayList<>();
        try (InputStream in = assets.open("structures/road_structures.json");
             JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("version".equals(key)) {
                    version = reader.nextString();
                } else if ("source".equals(key)) {
                    source = reader.nextString();
                } else if ("structures".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        RoadStructureRecord record = readStructure(reader);
                        if (record != null) records.add(record);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        baseRecords = Collections.unmodifiableList(records);
        return baseRecords;
    }

    private static RoadStructureRecord readStructure(JsonReader reader) throws IOException {
        String id = null;
        String type = "";
        String name = "";
        int layer = 0;
        List<GeoPoint> geometry = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id":
                    id = reader.nextString();
                    break;
                case "t":
                    type = reader.nextString();
                    break;
                case "n":
                    name = reader.nextString();
                    break;
                case "l":
                    layer = reader.nextInt();
                    break;
                case "g":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        double lon = reader.nextDouble();
                        double lat = reader.nextDouble();
                        geometry.add(new GeoPoint(lat, lon));
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
        if (id == null || geometry.size() < 2) return null;
        RoadStructure structure = "T".equals(type) ? RoadStructure.TUNNEL : RoadStructure.BRIDGE;
        return new RoadStructureRecord(id, structure, name, layer, geometry);
    }

    /**
     * Builds a fresh index with copies of the records, so status updates arriving later
     * never change an index another thread is using.
     */
    public StructureIndex buildIndex(Map<String, StructureStatusRepository.StatusRecord> statuses) throws IOException {
        List<RoadStructureRecord> copies = new ArrayList<>();
        for (RoadStructureRecord base : loadBaseBlocking()) {
            RoadStructureRecord copy = new RoadStructureRecord(base.getId(), base.getType(), base.getName(),
                    base.getLayer(), base.getGeometry());
            StructureStatusRepository.StatusRecord status = statuses == null ? null : statuses.get(base.getId());
            if (status != null) {
                copy.applyStatus(status.status, status.evidence, status.updatedAt, status.updatedBy);
            }
            copies.add(copy);
        }
        return new StructureIndex(copies, version);
    }

    public String getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }
}
