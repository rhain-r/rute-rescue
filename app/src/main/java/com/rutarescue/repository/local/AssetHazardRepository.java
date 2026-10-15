package com.rutarescue.repository.local;

import android.content.res.AssetManager;

import com.rutarescue.exception.MalformedDataException;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.persistence.PreparedDataManifest;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.risk.HazardGrid;
import com.rutarescue.risk.HazardLookup;
import com.rutarescue.util.SafeLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the prepared NOAH hazard grids bundled in assets/flood_data. Each grid is checked
 * against the SHA-256 hash recorded in manifest.json before use (DOCX section 7 gate
 * "Derived files match manifest hashes"), then kept in memory.
 */
public final class AssetHazardRepository implements HazardRepository {

    private static final String TAG = "RR-Hazard";

    private final AssetManager assets;
    private final Map<FloodScenario, HazardGrid> cache = new EnumMap<>(FloodScenario.class);
    private final PreparedDataManifest manifest;

    public AssetHazardRepository(AssetManager assets) {
        this.assets = assets;
        this.manifest = readManifest(assets);
    }

    private static PreparedDataManifest readManifest(AssetManager assets) {
        try (InputStream in = assets.open("flood_data/manifest.json")) {
            return PreparedDataManifest.read(in);
        } catch (IOException missing) {
            SafeLog.w(TAG, "Prepared data manifest is missing or invalid", missing);
            return PreparedDataManifest.empty();
        }
    }

    @Override
    public synchronized HazardLookup loadBlocking(FloodScenario scenario) throws IOException {
        HazardGrid cached = cache.get(scenario);
        if (cached != null) return cached;

        byte[] bytes;
        try (InputStream in = assets.open(scenario.getGridAssetPath())) {
            bytes = readAll(in);
        }
        PreparedDataManifest.ScenarioInfo info = manifest.get(scenario);
        if (info != null && !info.getGridSha256().isEmpty() && !info.getGridSha256().equalsIgnoreCase(sha256(bytes))) {
            throw new MalformedDataException("The prepared " + scenario.getLabel()
                    + " hazard grid does not match its manifest hash. Re-run :tools:dataprep:prepareHazards.");
        }
        HazardGrid grid = HazardGrid.read(new ByteArrayInputStream(bytes), scenario, manifest.versionOf(scenario));
        cache.put(scenario, grid);
        return grid;
    }

    @Override
    public String loadGeoJsonBlocking(FloodScenario scenario) throws IOException {
        byte[] bytes;
        try (InputStream in = assets.open(scenario.getGeoJsonAssetPath())) {
            bytes = readAll(in);
        }
        PreparedDataManifest.ScenarioInfo info = manifest.get(scenario);
        if (info != null && !info.getGeoJsonSha256().isEmpty() && !info.getGeoJsonSha256().equalsIgnoreCase(sha256(bytes))) {
            throw new MalformedDataException("The prepared " + scenario.getLabel()
                    + " map overlay does not match its manifest hash. Re-run :tools:dataprep:prepareHazards.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public PreparedDataManifest getManifest() {
        return manifest;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
