package com.rutarescue.ui.map;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.SampleVerdict;

/**
 * Base map style and colours.
 *
 * Basemap: OpenStreetMap raster tiles (free, no key, no payment; attribution
 * "© OpenStreetMap contributors"). Offline, the plain background shows instead and the prepared
 * NOAH overlays still render, because they are bundled in the app.
 *
 * Live traffic: with a TomTom key (free tier, no credit card), TomTom's traffic-flow tiles are
 * added as an overlay on the OpenStreetMap basemap. It starts hidden; MapController shows it for
 * motorcycle and car trips (green = free flow, orange/red = slow, dark red = standstill).
 */
public final class MapStyles {

    public static final String COLOR_CLEAR = "#12B5D0"; // Figma safer-route line (accent cyan)
    public static final String COLOR_LOW = "#E5B53A";
    public static final String COLOR_MEDIUM = "#F08C2E";
    public static final String COLOR_HIGH = "#E0483E";
    public static final String COLOR_UNVERIFIED = "#7C5CC4";
    public static final String COLOR_CLOSED = "#1B1B1F";
    public static final String COLOR_NO_DATA = "#7A8796";
    public static final String COLOR_OTHER_ROUTE = "#9AA6B2";

    /** Id of the TomTom live-traffic overlay layer (absent without a key). */
    public static final String TRAFFIC_LAYER = "traffic";

    private static final String OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    private MapStyles() {
    }

    public static String baseStyleJson(String tomTomKey) {
        String background = "{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#EEF1F6\"}}";
        StringBuilder sources = new StringBuilder("\"osm\":{\"type\":\"raster\",\"tiles\":[\"" + OSM_TILES + "\"],"
                + "\"tileSize\":256,\"maxzoom\":19,\"attribution\":\"© OpenStreetMap contributors\"}");
        StringBuilder layers = new StringBuilder(background
                + ",{\"id\":\"osm\",\"type\":\"raster\",\"source\":\"osm\",\"paint\":{\"raster-saturation\":-0.35}}");
        if (tomTomKey != null && !tomTomKey.isEmpty()) {
            String traffic = "https://api.tomtom.com/traffic/map/4/tile/flow/relative0/{z}/{x}/{y}.png?tileSize=256&key=" + tomTomKey;
            sources.append(",\"traffic\":{\"type\":\"raster\",\"tiles\":[\"").append(traffic)
                    .append("\"],\"tileSize\":256,\"attribution\":\"Traffic © TomTom\"}");
            layers.append(",{\"id\":\"").append(TRAFFIC_LAYER).append("\",\"type\":\"raster\",\"source\":\"traffic\",")
                    .append("\"layout\":{\"visibility\":\"none\"},\"paint\":{\"raster-opacity\":0.9}}");
        }
        return "{\"version\":8,\"name\":\"RutaRescue\",\"sources\":{" + sources + "},\"layers\":[" + layers + "]}";
    }

    /** Colour of a route piece after the structure-aware rules. */
    public static String colorFor(SampleVerdict verdict) {
        if (verdict.isClosed()) return COLOR_CLOSED;
        if (verdict.isOutOfCoverage()) return COLOR_NO_DATA;
        if (verdict.isUnverifiedStructure()) return COLOR_UNVERIFIED;
        HazardClass effective = verdict.getEffectiveHazard();
        switch (effective) {
            case HIGH: return COLOR_HIGH;
            case MEDIUM: return COLOR_MEDIUM;
            case LOW: return COLOR_LOW;
            default: return COLOR_CLEAR;
        }
    }
}
