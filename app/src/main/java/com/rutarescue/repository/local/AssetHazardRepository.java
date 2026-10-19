package com.rutarescue.repository.local;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the prepared NOAH hazard grids bundled in assets/flood_data. Each grid is checked
 * against the SHA-256 hash recorded in manifest.json before use (DOCX section 7 gate
 * "Derived files match manifest hashes"), then kept in memory.
 *
 * Thread safety (no UI freeze under heavy use): each scenario has its own lock, so loading the
 * 100-year grid never makes a lookup on the already-loaded 5-year grid wait. Many screens asking
 * for the same scenario at once still read and hash the file only once.
 */
public final class AssetHazardRepository implements HazardRepository {

    private static final String TAG = "RR-Hazard";

    private final AssetSource assets;
    /** Loaded grids; a ConcurrentHashMap so readers never need a lock. */
    private final Map<FloodScenario, HazardGrid> cache = new ConcurrentHashMap<>();
    /** One lock per scenario: only threads asking for the SAME scenario wait for its first load. */
    private final Map<FloodScenario, Object> loadLocks = new EnumMap<>(FloodScenario.class);
    private final PreparedDataManifest manifest;

    /** @param assets usually {@code context.getAssets()::open} */
    public AssetHazardRepository(AssetSource assets) {
        this.assets = assets;
        this.manifest = readManifest(assets);
        for (FloodScenario scenario : FloodScenario.values()) {
            loadLocks.put(scenario, new Object());
        }
    }

    private static PreparedDataManifest readManifest(AssetSource assets) {
        try (InputStream in = assets.open("flood_data/manifest.json")) {
            return PreparedDataManifest.read(in);
        } catch (IOException missing) {
            SafeLog.w(TAG, "Prepared data manifest is missing or invalid", missing);
            return PreparedDataManifest.empty();
        }
    }

    @Override
    public HazardLookup loadBlocking(FloodScenario scenario) throws IOException {
        HazardGrid cached = cache.get(scenario);
        if (cached != null) return cached; // fast path: already loaded, no waiting at all

        synchronized (loadLocks.get(scenario)) {
            cached = cache.get(scenario);
            if (cached != null) return cached; // another thread loaded it while this one waited
            HazardGrid grid = readGrid(scenario);
            cache.put(scenario, grid);
            return grid;
        }
    }

    /** Reads, hash-checks and decodes one grid file (about 0.5 MB compressed). */
    private HazardGrid readGrid(FloodScenario scenario) throws IOException {
        byte[] bytes;
        try (InputStream in = assets.open(scenario.getGridAssetPath())) {
            bytes = readAll(in);
        }
        PreparedDataManifest.ScenarioInfo info = manifest.get(scenario);
        if (info != null && !info.getGridSha256().isEmpty() && !info.getGridSha256().equalsIgnoreCase(sha256(bytes))) {
            throw new MalformedDataException("The prepared " + scenario.getLabel()
                    + " hazard grid does not match its manifest hash. Re-run :tools:dataprep:prepareHazards.");
        }
        return HazardGrid.read(new ByteArrayInputStream(bytes), scenario, manifest.versionOf(scenario));
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
