package com.rutarescue.ui.map;

import android.os.Bundle;
import android.view.Gravity;

import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.route.RoadClosure;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteSample;
import com.rutarescue.model.structure.RoadStructureRecord;
import com.rutarescue.model.structure.StructureStatus;
import com.rutarescue.repository.HazardRepository;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.SafeLog;

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
import org.maplibre.android.style.sources.GeoJsonOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Wraps a MapLibre MapView so screens can say "show this scenario", "show these routes",
 * "show these centers" without dealing with sources and layers themselves.
 *
 * Layers from bottom to top: basemap, NOAH LOW/MEDIUM/HIGH fills, bridges/tunnels, closures,
 * other routes, selected route (coloured by risk), centers, origin/destination, selection.
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

    private static final String TAG = "RR-Map";

    private static final String SRC_HAZARD = "rr-hazard";
    private static final String SRC_STRUCTURES = "rr-structures";
    private static final String SRC_CLOSURES = "rr-closures";
    private static final String SRC_ROUTES = "rr-routes";
    private static final String SRC_CENTERS = "rr-centers";
    private static final String SRC_MARKERS = "rr-markers";

    private static final String[] HAZARD_LAYERS = {"rr-hazard-low", "rr-hazard-medium", "rr-hazard-high"};

    private final MapView mapView;
    private final String tomTomKey;
    private MapLibreMap map;
    private Style style;
    private FloodScenario hazardScenario;
    private PointListener clickListener;
    private PointListener longClickListener;

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
                ready.onReady(this);
            });
        });
    }

    private void addSourcesAndLayers() {
        // NOAH display overlay (bundled GeoJSON; filled in by showHazard()).
        style.addSource(new GeoJsonSource(SRC_HAZARD, FeatureCollection.fromFeatures(new ArrayList<>()),
                new GeoJsonOptions().withMaxZoom(15).withTolerance(0.6f)));
        String[] colors = {MapStyles.COLOR_LOW, MapStyles.COLOR_MEDIUM, MapStyles.COLOR_HIGH};
        for (int i = 0; i < HAZARD_LAYERS.length; i++) {
            style.addLayer(new FillLayer(HAZARD_LAYERS[i], SRC_HAZARD)
                    .withFilter(Expression.eq(Expression.get("cls"), Expression.literal(i + 1)))
                    .withProperties(PropertyFactory.fillColor(colors[i]), PropertyFactory.fillOpacity(0.42f)));
        }

        style.addSource(new GeoJsonSource(SRC_STRUCTURES, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new LineLayer("rr-structures", SRC_STRUCTURES).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND)));

        style.addSource(new GeoJsonSource(SRC_CLOSURES, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new CircleLayer("rr-closures", SRC_CLOSURES).withProperties(
                PropertyFactory.circleColor(MapStyles.COLOR_CLOSED), PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f)));

        style.addSource(new GeoJsonSource(SRC_ROUTES, FeatureCollection.fromFeatures(new ArrayList<>())));
        style.addLayer(new LineLayer("rr-routes-other", SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("selected"), Expression.literal(0)))
                .withProperties(PropertyFactory.lineColor(MapStyles.COLOR_OTHER_ROUTE), PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineOpacity(0.8f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
        style.addLayer(new LineLayer("rr-routes-selected", SRC_ROUTES)
                .withFilter(Expression.eq(Expression.get("selected"), Expression.literal(1)))
                .withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.lineWidth(7f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));

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

    /**
     * Switches the NOAH overlay to another scenario. The bundled GeoJSON (5-7 MB) is read on
     * a background thread and handed to MapLibre on the UI thread. A newer request wins
     * if the user switches scenarios quickly.
     */
    public void showHazard(FloodScenario scenario, HazardRepository hazards, AppExecutors executors) {
        if (!isReady() || scenario == hazardScenario) return;
        hazardScenario = scenario;
        executors.io().execute(() -> {
            try {
                String geoJson = hazards.loadGeoJsonBlocking(scenario);
                executors.main().execute(() -> {
                    if (!isReady() || scenario != hazardScenario) return;
                    GeoJsonSource source = style.getSourceAs(SRC_HAZARD);
                    if (source != null) source.setGeoJson(geoJson);
                });
            } catch (IOException error) {
                SafeLog.w(TAG, "Could not load the " + scenario.getLabel() + " overlay", error);
                executors.main().execute(() -> {
                    if (scenario == hazardScenario) hazardScenario = null;
                });
            }
        });
    }

    public void setHazardVisible(boolean visible) {
        for (String id : HAZARD_LAYERS) setVisible(id, visible);
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

    /** Draws all alternatives; the selected one is coloured piece by piece by its risk. */
    public void showRoutes(List<RouteAssessment> routes, int selectedIndex) {
        if (!isReady()) return;
        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            if (i == selectedIndex) continue;
            Feature other = Feature.fromGeometry(LineString.fromLngLats(points(routes.get(i).getCandidate().getPoints())));
            other.addNumberProperty("selected", 0);
            features.add(other);
        }
        if (selectedIndex >= 0 && selectedIndex < routes.size()) {
            features.addAll(coloredRuns(routes.get(selectedIndex).getSamples()));
        }
        setFeatures(SRC_ROUTES, features);
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
