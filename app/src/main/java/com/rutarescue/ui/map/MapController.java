package com.rutarescue.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.rutarescue.R;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.map.BaseMap;
import com.rutarescue.model.map.MapLayerSettings;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.GeoMath;
import com.rutarescue.util.SafeLog;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps a MapLibre MapView so screens can say "show this scenario", "show these routes",
 * "show these centers" without dealing with sources and layers themselves.
 *
 * Layers from bottom to top: base map (Standard, Topographic or Satellite), NOAH LOW/MEDIUM/HIGH
 * fills for each scenario (100-year at the bottom, 5-year on top), bridges/tunnels, closures,
 * other routes, selected route (coloured by risk), route time pills, centers, origin/destination.
 */
public final class MapController {

    /** Called once the style and all layers are ready. */
    public interface ReadyListener {
        void onReady(MapController controller);
    }

    /** Receives a tapped map position. */
    public interface PointListener {
        void onPoint(GeoPoint point);
    }

    public static final GeoPoint METRO_MANILA_CENTER = new GeoPoint(14.5995, 120.9842);
    /** Just below the Figma "Map Controls" (Locate + Layers end at about 310 dp). */
    private static final int ATTRIBUTION_TOP_DP = 318;

    private static final String SRC_STRUCTURES = "rr-structures";
    private static final String SRC_CLOSURES = "rr-closures";
    private static final String SRC_ROUTES = "rr-routes";
    private static final String SRC_CENTERS = "rr-centers";
    private static final String SRC_MARKERS = "rr-markers";
    private static final String SRC_ROUTE_LABELS = "rr-route-labels";
    private static final String SRC_TRANSIT = "rr-transit";
    /** Bundled LRT/MRT lines and stations (OpenStreetMap, see data/sources/osm-rail-2026-10-19). */
    private static final String TRANSIT_ASSET = "transit/metro_manila_rail.geojson";
    private static final String[] TRANSIT_LAYERS = {"rr-transit-casing", "rr-transit-lines", "rr-transit-stations",
            "rr-transit-labels"};

    /** Drawn first to last, so the smaller, more frequent flood sits on top. */
    private static final FloodScenario[] FLOOD_DRAW_ORDER = {
            FloodScenario.HUNDRED_YEAR, FloodScenario.TWENTY_FIVE_YEAR, FloodScenario.FIVE_YEAR};
    private static final String[] HAZARD_CLASSES = {"low", "medium", "high"};

    private final MapView mapView;
    private final String tomTomKey;
    private MapLibreMap map;
    private Style style;
    /** Reads each scenario's GeoJSON once, off the UI thread (created on the first showFloods). */
    private FloodOverlayLoader floodLoader;
    private final EnumSet<FloodScenario> visibleFloods = EnumSet.noneOf(FloodScenario.class);
    private boolean floodsShown = true;
    private PointListener clickListener;
    private PointListener longClickListener;
    /** Runs when the resident starts zooming or panning (not when the app moves the camera). */
    private Runnable userMoveListener;
    /** The train map was read (or is being read) from the app's assets. */
    private boolean transitRequested;

    public MapController(MapView mapView, String tomTomKey) {
        this.mapView = mapView;
        this.tomTomKey = tomTomKey;
    }

    public void onCreate(Bundle savedState, ReadyListener ready) {
        mapView.onCreate(savedState);
        mapView.getMapAsync(loadedMap -> {
            map = loadedMap;
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            placeAttribution();
            map.setStyle(new Style.Builder().fromJson(MapStyles.baseStyleJson(tomTomKey)), loadedStyle -> {
                style = loadedStyle;
                addSourcesAndLayers();
                moveTo(METRO_MANILA_CENTER, 11.5);
                map.addOnMapClickListener(latLng -> {
                    if (clickListener != null) clickListener.onPoint(toGeo(latLng));
                    return clickListener != null;
                });
                map.addOnMapLongClickListener(latLng -> {
                    if (longClickListener != null) longClickListener.onPoint(toGeo(latLng));
                    return longClickListener != null;
                });
                map.addOnCameraMoveStartedListener(reason -> {
                    // Only finger gestures (pinch, double-tap, drag), never the app's own camera moves.
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                            && userMoveListener != null) {
                        userMoveListener.run();
                    }
                });
                ready.onReady(this);
            });
        });
    }

    private void addSourcesAndLayers() {
        // NOAH display overlays, one source per scenario (bundled GeoJSON, loaded when first shown).
        String[] colors = {MapStyles.COLOR_LOW, MapStyles.COLOR_MEDIUM, MapStyles.COLOR_HIGH};
        for (FloodScenario scenario : FLOOD_DRAW_ORDER) {
            style.addSource(new GeoJsonSource(hazardSource(scenario), FeatureCollection.fromFeatures(new ArrayList<>()),
                    new GeoJsonOptions().withMaxZoom(15).withTolerance(0.6f)));
            for (int i = 0; i < HAZARD_CLASSES.length; i++) {
                style.addLayer(new FillLayer(hazardLayer(scenario, i), hazardSource(scenario))
                        .withFilter(Expression.eq(Expression.get("cls"), Expression.literal(i + 1)))
                        .withProperties(PropertyFactory.fillColor(colors[i]), PropertyFactory.fillOpacity(0.42f),
                                PropertyFactory.visibility(Property.NONE)));
            }
        }

        // Train lines for commuters: white casing + line colour, station dots, then station names.
        style.addSource(new GeoJsonSource(SRC_TRANSIT, FeatureCollection.fromFeatures(new ArrayList<>())));
        Expression isLine = Expression.eq(Expression.get("kind"), Expression.literal("line"));
        Expression isStation = Expression.eq(Expression.get("kind"), Expression.literal("station"));
        style.addLayer(new LineLayer("rr-transit-casing", SRC_TRANSIT).withFilter(isLine).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 4f), Expression.stop(15, 10f))),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.visibility(Property.NONE)));
        style.addLayer(new LineLayer("rr-transit-lines", SRC_TRANSIT).withFilter(isLine).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("colour"))),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 2.5f), Expression.stop(15, 6f))),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.visibility(Property.NONE)));
        style.addLayer(new CircleLayer("rr-transit-stations", SRC_TRANSIT).withFilter(isStation).withProperties(
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 2.5f), Expression.stop(15, 6f))),
                PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("colour"))),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.visibility(Property.NONE)));
        // Station names from street-ish zoom; a name that would overlap another is hidden.
        SymbolLayer stationNames = new SymbolLayer("rr-transit-labels", SRC_TRANSIT).withFilter(isStation).withProperties(
                PropertyFactory.iconImage(Expression.get("label")), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_LEFT),
                PropertyFactory.iconOffset(new Float[]{8f, 0f}), PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.visibility(Property.NONE));
        stationNames.setMinZoom(13f);
        style.addLayer(stationNames);

        style.addSource(new GeoJsonSource(SRC_STRUCTURES, FeatureCollection.fromFeatures(new ArrayList<>())));
        // Thin and faint at city zoom (5,000+ bridges), clearer when zoomed in to street level.
        style.addLayer(new LineLayer("rr-structures", SRC_STRUCTURES).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 1f), Expression.stop(13, 2.5f), Expression.stop(16, 5f))),
                PropertyFactory.lineOpacity(Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 0.3f), Expression.stop(14, 0.85f))),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)));

        style.addSource(new GeoJsonSource(SRC_CLOSURES, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new CircleLayer("rr-closures", SRC_CLOSURES).withProperties(
                PropertyFactory.circleColor(MapStyles.COLOR_CLOSED), PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f)));

        style.addSource(new GeoJsonSource(SRC_ROUTES, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new LineLayer("rr-routes-other", SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("selected"), Expression.literal(0)))
                .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineOpacity(0.6f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
        style.addLayer(new LineLayer("rr-routes-selected", SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("selected"), Expression.literal(1)))
                .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.lineWidth(7f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));

        // Phase 1 route time pills ("41 min"): one small image per route, drawn by routePill().
        style.addSource(new GeoJsonSource(SRC_ROUTE_LABELS, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new SymbolLayer("rr-route-labels", SRC_ROUTE_LABELS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)));

        style.addSource(new GeoJsonSource(SRC_CENTERS, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new CircleLayer("rr-centers", SRC_CENTERS).withProperties(
                PropertyFactory.circleColor("#15A38B"), PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)));

        style.addSource(new GeoJsonSource(SRC_MARKERS, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new CircleLayer("rr-markers", SRC_MARKERS).withProperties(
                PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))), PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)));
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * The Figma map screens cover the bottom corners with the floating sheet and the navigation
     * bar, so the MapLibre logo and the data attribution button move to the right edge, just
     * below the Locate / Layers buttons, where they stay visible (attribution is required).
     */
    private void placeAttribution() {
        float density = mapView.getResources().getDisplayMetrics().density;
        int top = Math.round(ATTRIBUTION_TOP_DP * density);
        int end = Math.round(18 * density);
        map.getUiSettings().setAttributionGravity(Gravity.TOP | Gravity.END);
        map.getUiSettings().setAttributionMargins(0, top, end, 0);
        map.getUiSettings().setLogoGravity(Gravity.TOP | Gravity.END);
        map.getUiSettings().setLogoMargins(0, top, end + Math.round(28 * density), 0);
    }

    public void onStart() { mapView.onStart(); }
    public void onResume() { mapView.onResume(); }
    public void onPause() { mapView.onPause(); }
    public void onStop() { mapView.onStop(); }
    public void onLowMemory() { mapView.onLowMemory(); }
    public void onDestroy() {
        // Forget the style first so late background results (e.g. the hazard overlay) are ignored.
        style = null;
        mapView.onDestroy();
    }
    public void onSaveInstanceState(Bundle outState) { mapView.onSaveInstanceState(outState); }

    public boolean isReady() {
        return style != null;
    }

    // ------------------------------------------------------------------ content

    /** Shows exactly one NOAH scenario (LGU map scenario pills). */
    public void showHazard(FloodScenario scenario, HazardRepository hazards, AppExecutors executors) {
        showFloods(EnumSet.of(scenario), hazards, executors);
    }

    /**
     * Shows any mix of NOAH scenarios (Phase 1 "Map layers" switches). Each bundled GeoJSON
     * (5-7 MB) is read once on a background thread (FloodOverlayLoader) and handed to MapLibre
     * on the UI thread; MapLibre then parses it on its own worker thread.
     * With more than one scenario the fills are lighter so the layers stay readable.
     */
    public void showFloods(Set<FloodScenario> scenarios, HazardRepository hazards, AppExecutors executors) {
        if (!isReady()) return;
        visibleFloods.clear();
        visibleFloods.addAll(scenarios);
        refreshFloodLayers();
        if (floodLoader == null) floodLoader = new FloodOverlayLoader(hazards, executors, this::applyOverlay);
        for (FloodScenario scenario : scenarios) {
            floodLoader.request(scenario);
        }
    }

    /** Puts a loaded overlay into its map source (UI thread). */
    private void applyOverlay(FloodScenario scenario, String geoJson) {
        if (!isReady()) return;
        GeoJsonSource source = style.getSourceAs(hazardSource(scenario));
        if (source != null) source.setGeoJson(geoJson);
    }

    /** Hides or shows every chosen flood scenario at once (LGU "Risk zones" pill). */
    public void setHazardVisible(boolean visible) {
        floodsShown = visible;
        refreshFloodLayers();
    }

    private void refreshFloodLayers() {
        if (!isReady()) return;
        float opacity = visibleFloods.size() > 1 ? 0.30f : 0.42f;
        for (FloodScenario scenario : FLOOD_DRAW_ORDER) {
            boolean on = floodsShown && visibleFloods.contains(scenario);
            for (int i = 0; i < HAZARD_CLASSES.length; i++) {
                Layer layer = style.getLayer(hazardLayer(scenario, i));
                if (layer != null) {
                    layer.setProperties(PropertyFactory.visibility(on ? Property.VISIBLE : Property.NONE),
                            PropertyFactory.fillOpacity(opacity));
                }
            }
        }
    }

    private static String hazardSource(FloodScenario scenario) {
        return "rr-hazard-" + scenario.getReturnPeriodYears();
    }

    private static String hazardLayer(FloodScenario scenario, int classIndex) {
        return hazardSource(scenario) + "-" + HAZARD_CLASSES[classIndex];
    }

    /** Switches between the Standard, Topographic and Satellite base maps. */
    public void setBaseMap(BaseMap baseMap) {
        setVisible(MapStyles.BASE_STANDARD, baseMap == BaseMap.STANDARD);
        setVisible(MapStyles.BASE_TOPOGRAPHIC, baseMap == BaseMap.TOPOGRAPHIC);
        setVisible(MapStyles.BASE_SATELLITE, baseMap == BaseMap.SATELLITE);
    }

    /** Applies everything chosen in the Map layers sheet. */
    public void applyLayers(MapLayerSettings settings, HazardRepository hazards, AppExecutors executors) {
        setBaseMap(settings.getBaseMap());
        EnumSet<FloodScenario> floods = EnumSet.noneOf(FloodScenario.class);
        for (FloodScenario scenario : FloodScenario.values()) {
            if (settings.isFloodVisible(scenario)) floods.add(scenario);
        }
        floodsShown = true;
        showFloods(floods, hazards, executors);
        setCentersVisible(settings.isCentersVisible());
        setStructuresVisible(settings.isStructuresVisible());
        setTrafficVisible(settings.isTrafficVisible());
        setTransitVisible(settings.isTransitVisible(), executors);
    }

    /**
     * Shows or hides the train lines. The bundled map (about 26 KB) is read once, off the UI
     * thread, the first time the switch is turned on; station names are drawn as small images
     * because the offline map style has no fonts.
     */
    public void setTransitVisible(boolean visible, AppExecutors executors) {
        for (String layer : TRANSIT_LAYERS) setVisible(layer, visible);
        if (!visible || transitRequested || !isReady()) return;
        transitRequested = true;
        Context context = mapView.getContext();
        executors.io().execute(() -> {
            try (InputStream in = context.getAssets().open(TRANSIT_ASSET)) {
                FeatureCollection rail = FeatureCollection.fromJson(new String(readAll(in), StandardCharsets.UTF_8));
                HashMap<String, Bitmap> labels = new HashMap<>();
                List<Feature> features = rail.features() == null ? new ArrayList<>() : rail.features();
                for (Feature feature : features) {
                    if (!"station".equals(feature.getStringProperty("kind"))) continue;
                    String name = feature.getStringProperty("name");
                    String id = "rr-station-" + feature.getStringProperty("line") + "-" + name;
                    feature.addStringProperty("label", id);
                    labels.put(id, stationLabel(context, name, feature.getStringProperty("colour")));
                }
                executors.main().execute(() -> {
                    if (!isReady()) return;
                    style.addImages(labels);
                    GeoJsonSource source = style.getSourceAs(SRC_TRANSIT);
                    if (source != null) source.setGeoJson(FeatureCollection.fromFeatures(features));
                });
            } catch (IOException | RuntimeException error) {
                SafeLog.w("RR-Map", "Could not load the train map", error);
                executors.main().execute(() -> transitRequested = false); // try again next time
            }
        });
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    /** A station name on a white pill with a dot in the line's colour (no map fonts offline). */
    private static Bitmap stationLabel(Context context, String name, String colour) {
        float density = context.getResources().getDisplayMetrics().density;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(ResourcesCompat.getFont(context, R.font.karla_semibold));
        textPaint.setTextSize(11 * density);
        textPaint.setColor(0xFF162235);
        float dot = 6 * density;
        float width = textPaint.measureText(name) + dot + 18 * density;
        float height = 20 * density;
        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(width + 2 * density), (int) Math.ceil(height + 2 * density),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF box = new RectF(density, density, width + density, height + density);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xF2FFFFFF);
        canvas.drawRoundRect(box, height / 2, height / 2, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        stroke.setColor(0xFFE8EAED);
        canvas.drawRoundRect(box, height / 2, height / 2, stroke);
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        try {
            dotPaint.setColor(Color.parseColor(colour));
        } catch (IllegalArgumentException | NullPointerException unknownColour) {
            dotPaint.setColor(0xFF24547F);
        }
        canvas.drawCircle(box.left + 7 * density + dot / 2, box.centerY(), dot / 2, dotPaint);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = box.centerY() - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(name, box.left + 11 * density + dot, baseline, textPaint);
        return bitmap;
    }

    /** Shows or hides the TomTom live-traffic overlay (does nothing without a TomTom key). */
    public void setTrafficVisible(boolean visible) {
        setVisible(MapStyles.TRAFFIC_LAYER, visible);
    }

    public void setStructuresVisible(boolean visible) {
        setVisible("rr-structures", visible);
    }

    public void setCentersVisible(boolean visible) {
        setVisible("rr-centers", visible);
    }

    private void setVisible(String layerId, boolean visible) {
        if (!isReady()) return;
        Layer layer = style.getLayer(layerId);
        if (layer != null) layer.setProperties(PropertyFactory.visibility(visible ? Property.VISIBLE : Property.NONE));
    }

    /** Draws all alternatives in grey; the selected one is coloured piece by piece by its risk. */
    public void showRoutes(List<RouteAssessment> routes, int selectedIndex) {
        List<String> grey = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) grey.add(MapStyles.COLOR_OTHER_ROUTE);
        showRoutes(routes, selectedIndex, grey, null);
    }

    /**
     * Phase 1 route comparison: each other route in its own colour (cyan lowest risk, brand
     * balanced, grey most direct) at 60%, the selected route coloured by risk, and a time pill
     * ("41 min") halfway along each route (navy for the selected one).
     */
    public void showRoutes(List<RouteAssessment> routes, int selectedIndex, List<String> colors, List<String> pills) {
        if (!isReady()) return;
        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            if (i == selectedIndex) continue;
            Feature other = Feature.fromGeometry(LineString.fromLngLats(points(routes.get(i).getCandidate().getPoints())));
            other.addNumberProperty("selected", 0);
            other.addStringProperty("color", colors.get(i));
            features.add(other);
        }
        if (selectedIndex >= 0 && selectedIndex < routes.size()) {
            features.addAll(coloredRuns(routes.get(selectedIndex).getSamples()));
        }
        setFeatures(SRC_ROUTES, features);

        List<Feature> labels = new ArrayList<>();
        if (pills != null) {
            for (int i = 0; i < routes.size(); i++) {
                String icon = "rr-pill-" + i + (i == selectedIndex ? "-on" : "");
                style.addImage(icon, routePill(mapView.getContext(), pills.get(i), i == selectedIndex));
                Feature label = Feature.fromGeometry(point(halfway(routes.get(i).getCandidate().getPoints())));
                label.addStringProperty("icon", icon);
                labels.add(label);
            }
        }
        setFeatures(SRC_ROUTE_LABELS, labels);
    }

    /** The point halfway along a line (by distance). */
    private static GeoPoint halfway(List<GeoPoint> line) {
        double total = 0;
        for (int i = 1; i < line.size(); i++) total += GeoMath.distanceMeters(line.get(i - 1), line.get(i));
        double walked = 0;
        for (int i = 1; i < line.size(); i++) {
            double step = GeoMath.distanceMeters(line.get(i - 1), line.get(i));
            if (walked + step >= total / 2) return line.get(i);
            walked += step;
        }
        return line.get(line.size() / 2);
    }

    /** Draws a Figma time pill: 22 dp high, Karla Bold 11 sp, navy (selected) or white with a border. */
    private static Bitmap routePill(Context context, String text, boolean selected) {
        float density = context.getResources().getDisplayMetrics().density;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface karla = ResourcesCompat.getFont(context, R.font.karla_bold);
        textPaint.setTypeface(karla);
        textPaint.setTextSize(11 * density);
        textPaint.setColor(selected ? 0xFFFFFFFF : 0xFF162235);
        float width = textPaint.measureText(text) + 20 * density;
        float height = 22 * density;
        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(width + 2 * density), (int) Math.ceil(height + 2 * density),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF box = new RectF(density, density, width + density, height + density);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(selected ? 0xFF152B3F : 0xFFFFFFFF);
        canvas.drawRoundRect(box, height / 2, height / 2, fill);
        if (!selected) {
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(density);
            stroke.setColor(0xFFE8EAED);
            canvas.drawRoundRect(box, height / 2, height / 2, stroke);
        }
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = box.centerY() - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(text, box.left + 10 * density, baseline, textPaint);
        return bitmap;
    }

    /** Groups consecutive samples of the same colour into one line feature. */
    private static List<Feature> coloredRuns(List<RouteSample> samples) {
        List<Feature> runs = new ArrayList<>();
        List<Point> current = new ArrayList<>();
        String currentColor = null;
        for (RouteSample sample : samples) {
            String color = MapStyles.colorFor(sample.getVerdict());
            Point point = Point.fromLngLat(sample.getPoint().getLongitude(), sample.getPoint().getLatitude());
            if (currentColor != null && !color.equals(currentColor)) {
                current.add(point); // close the gap between runs
                runs.add(run(current, currentColor));
                current = new ArrayList<>();
            }
            current.add(point);
            currentColor = color;
        }
        if (currentColor != null && current.size() >= 2) runs.add(run(current, currentColor));
        return runs;
    }

    private static Feature run(List<Point> points, String color) {
        if (points.size() == 1) points.add(points.get(0));
        Feature feature = Feature.fromGeometry(LineString.fromLngLats(points));
        feature.addNumberProperty("selected", 1);
        feature.addStringProperty("color", color);
        return feature;
    }

    public void clearRoutes() {
        setFeatures(SRC_ROUTES, new ArrayList<>());
        setFeatures(SRC_ROUTE_LABELS, new ArrayList<>());
    }

    public void showCenters(List<EvacuationCenter> centers) {
        List<Feature> features = new ArrayList<>();
        for (EvacuationCenter center : centers) {
            Feature feature = Feature.fromGeometry(point(center.getLocation()));
            feature.addStringProperty("name", center.getName());
            features.add(feature);
        }
        setFeatures(SRC_CENTERS, features);
    }

    public void showClosures(List<RoadClosure> closures) {
        List<Feature> features = new ArrayList<>();
        for (RoadClosure closure : closures) features.add(Feature.fromGeometry(point(closure.getLocation())));
        setFeatures(SRC_CLOSURES, features);
    }

    /** Bridges and tunnels coloured by their LGU status (LGU Hazard Map). */
    public void showStructures(Collection<RoadStructureRecord> structures) {
        List<Feature> features = new ArrayList<>();
        for (RoadStructureRecord record : structures) {
            Feature feature = Feature.fromGeometry(LineString.fromLngLats(points(record.getGeometry())));
            String color = record.getStatus() == StructureStatus.CLOSED ? MapStyles.COLOR_CLOSED
                    : record.getStatus() == StructureStatus.VERIFIED_OPEN ? "#1E9E6A" : MapStyles.COLOR_UNVERIFIED;
            feature.addStringProperty("color", color);
            features.add(feature);
        }
        setFeatures(SRC_STRUCTURES, features);
    }

    /** Origin (blue), destination (navy) and an optional selected point (teal). */
    public void showMarkers(GeoPoint origin, GeoPoint destination, GeoPoint selected) {
        List<Feature> features = new ArrayList<>();
        if (origin != null) features.add(marker(origin, "#24547F"));
        if (destination != null) features.add(marker(destination, "#152B3F"));
        if (selected != null) features.add(marker(selected, "#12B5D0"));
        setFeatures(SRC_MARKERS, features);
    }

    private static Feature marker(GeoPoint location, String color) {
        Feature feature = Feature.fromGeometry(point(location));
        feature.addStringProperty("color", color);
        return feature;
    }

    private void setFeatures(String sourceId, List<Feature> features) {
        if (!isReady()) return;
        GeoJsonSource source = style.getSourceAs(sourceId);
        if (source != null) source.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    // ------------------------------------------------------------------ camera

    public void moveTo(GeoPoint point, double zoom) {
        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(toLatLng(point), zoom));
    }

    /** Where "you" sit in the visible map: two-fifths of the way down, a little above its middle. */
    private static final double YOU_FROM_TOP = 0.4;

    /**
     * Puts the point in the part of the map the resident can actually see: below the controls on
     * top (topCover) and above the card at the bottom (bottomCover, wherever it is now, even slid
     * down). It lands on the screen's centre line (under the card's "–" handle), two-fifths of the
     * way down that visible part, so it never looks pushed against the card. Zooms in to at least
     * minZoom, but never zooms out if already closer.
     */
    public void centerOn(GeoPoint point, double minZoom, View topCover, View bottomCover) {
        if (map == null) return;
        int[] mapAt = new int[2];
        mapView.getLocationInWindow(mapAt);
        int top = 0;
        int bottom = 0;
        if (topCover != null && topCover.getVisibility() == View.VISIBLE) {
            int[] at = new int[2];
            topCover.getLocationInWindow(at);
            top = Math.max(0, at[1] + topCover.getHeight() - mapAt[1]);
        }
        if (bottomCover != null && bottomCover.getVisibility() == View.VISIBLE) {
            int[] at = new int[2];
            bottomCover.getLocationInWindow(at); // includes the card's slide (translation)
            bottom = Math.max(0, mapAt[1] + mapView.getHeight() - at[1]);
        }
        // The camera centres the point inside the padded area; extra bottom padding lifts it from
        // the middle of the visible band (0.5) to YOU_FROM_TOP of the way down.
        int visible = Math.max(0, mapView.getHeight() - top - bottom);
        int lift = (int) Math.round(visible * (1 - 2 * YOU_FROM_TOP));
        double zoom = Math.max(minZoom, map.getCameraPosition().zoom);
        map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(toLatLng(point)).zoom(zoom).padding(0, top, 0, bottom + lift).build()), 600);
    }

    public void fitTo(List<GeoPoint> points, int paddingPx) {
        fitTo(points, paddingPx, paddingPx, paddingPx, paddingPx);
    }

    /**
     * Fits the points inside the part of the map that is not covered by the floating controls
     * (top) and the Figma bottom sheet (bottom). Paddings are in pixels.
     */
    public void fitTo(List<GeoPoint> points, int left, int top, int right, int bottom) {
        if (map == null || points.isEmpty()) return;
        if (points.size() == 1) {
            moveTo(points.get(0), 15);
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (GeoPoint point : points) builder.include(toLatLng(point));
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), left, top, right, bottom));
    }

    public GeoPoint cameraCenter() {
        if (map == null || map.getCameraPosition().target == null) return METRO_MANILA_CENTER;
        return toGeo(map.getCameraPosition().target);
    }

    public void setOnMapClick(PointListener listener) {
        clickListener = listener;
    }

    public void setOnMapLongClick(PointListener listener) {
        longClickListener = listener;
    }

    /** Called when the resident starts zooming or panning the map with a finger. */
    public void setOnUserMove(Runnable listener) {
        userMoveListener = listener;
    }

    // ------------------------------------------------------------------ helpers

    private static List<Point> points(List<GeoPoint> line) {
        List<Point> points = new ArrayList<>(line.size());
        for (GeoPoint point : line) points.add(Point.fromLngLat(point.getLongitude(), point.getLatitude()));
        return points;
    }

    private static Point point(GeoPoint location) {
        return Point.fromLngLat(location.getLongitude(), location.getLatitude());
    }

    private static LatLng toLatLng(GeoPoint point) {
        return new LatLng(point.getLatitude(), point.getLongitude());
    }

    private static GeoPoint toGeo(LatLng latLng) {
        return new GeoPoint(latLng.getLatitude(), latLng.getLongitude());
    }
}
