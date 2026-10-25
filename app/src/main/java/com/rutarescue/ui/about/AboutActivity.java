package com.rutarescue.ui.about;

import android.os.Bundle;
import android.widget.TextView;

import com.rutarescue.R;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.persistence.PreparedDataManifest;
import com.rutarescue.ui.common.BaseActivity;

/**
 * Account, limitations (DOCX section 14), prepared data versions with their hashes, and
 * attribution for Project NOAH, OpenStreetMap (map, search, routing), TomTom (traffic) and MapLibre.
 * Opened from Settings; since 2026-10-22 Sign out is only in Settings.
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        applySystemBarInsets(findViewById(R.id.about_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.about_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());

        UserProfile profile = container().session() == null ? null : container().session().getProfile();
        ((TextView) findViewById(R.id.about_account)).setText(profile == null ? "Not signed in" : profile.getDisplayName());
        ((TextView) findViewById(R.id.about_role)).setText(profile == null ? "" : profile.getRoleLabel()
                + (profile.getCity() == null ? "" : " • " + profile.getCity().getDisplayName()));

        ((TextView) findViewById(R.id.about_limits)).setText(
                "RutaRescue supports flood preparedness decisions. It compares available routes and labels the best "
                        + "one \"" + getString(R.string.safer_label) + "\" - it never guarantees a safe route.\n\n"
                        + "It does NOT: predict exact floods, show live flooding (the NOAH maps are prepared scenarios), "
                        + "dispatch rescuers, replace emergency services, or know live evacuation-center capacity.\n\n"
                        + "Ligtas Priority in this class demo uses synthetic proof images only.\n\nEmergency: call 911.");

        PreparedDataManifest manifest = container().hazards.getManifest();
        StringBuilder data = new StringBuilder();
        for (FloodScenario scenario : FloodScenario.values()) {
            PreparedDataManifest.ScenarioInfo info = manifest.get(scenario);
            data.append(scenario.getLabel()).append(" scenario: ").append(manifest.versionOf(scenario)).append('\n');
            if (info != null) {
                data.append("  grid SHA-256 ").append(shortHash(info.getGridSha256())).append('\n')
                        .append("  source .shp SHA-256 ").append(shortHash(info.getSourceShpSha256())).append('\n');
            }
        }
        data.append("Prepared on ").append(manifest.getGeneratedAt().isEmpty() ? "unknown" : manifest.getGeneratedAt())
                .append("\nBridges/tunnels: ")
                .append(container().structures.getVersion().isEmpty() ? "loads on first route" : container().structures.getVersion());
        ((TextView) findViewById(R.id.about_data)).setText(data);

        ((TextView) findViewById(R.id.about_attribution)).setText(
                manifest.getAttribution() + "\n" + manifest.getLicense() + "\n\n"
                        + "Bridges and tunnels: © OpenStreetMap contributors, available under the Open Database License (ODbL).\n\n"
                        + "Basemap: © OpenStreetMap contributors (ODbL), tile.openstreetmap.org.\n"
                        + "Place search: Nominatim (OpenStreetMap).\n"
                        + "Routing without a TomTom key, or as backup: OSRM on routing.openstreetmap.de (OpenStreetMap data).\n"
                        + "Routing with live traffic and the traffic overlay: © TomTom (free developer tier).\n\n"
                        + "Map rendering: MapLibre Native (BSD-2-Clause).");

    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() < 12 ? "n/a" : hash.substring(0, 12) + "…";
    }
}
