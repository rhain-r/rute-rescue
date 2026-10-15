package com.rutarescue.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.IndexRange;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RouteCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a TomTom Routing API "calculateRoute" JSON answer into RouteCandidate objects.
 * Kept separate from the HTTP code so it can be unit-tested with a saved JSON string.
 *
 * Fields used: routes[].summary (length, time, traffic delay), routes[].legs[].points,
 * routes[].sections[] (TUNNEL, and TRAFFIC with simpleCategory ROAD_CLOSURE) and
 * routes[].guidance.instructions[] (text maneuvers).
 */
public final class TomTomRouteParser {

    private TomTomRouteParser() {
    }

    public static List<RouteCandidate> parse(String json, boolean trafficConsidered) throws ProviderException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray routes = root.getAsJsonArray("routes");
            if (routes == null || routes.size() == 0) {
                throw new ProviderException("No route was found between these points.", -1);
            }
            List<RouteCandidate> candidates = new ArrayList<>();
            for (int index = 0; index < routes.size(); index++) {
                candidates.add(parseRoute(routes.get(index).getAsJsonObject(), index, trafficConsidered));
            }
            return candidates;
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            throw new ProviderException("The routing answer could not be read.", -1, e);
        }
    }

    private static RouteCandidate parseRoute(JsonObject route, int index, boolean trafficConsidered) {
        JsonObject summary = route.getAsJsonObject("summary");
        double length = number(summary, "lengthInMeters");
        long time = (long) number(summary, "travelTimeInSeconds");
        long delay = (long) number(summary, "trafficDelayInSeconds");

        List<GeoPoint> points = new ArrayList<>();
        for (JsonElement legElement : route.getAsJsonArray("legs")) {
            for (JsonElement pointElement : legElement.getAsJsonObject().getAsJsonArray("points")) {
                JsonObject point = pointElement.getAsJsonObject();
                GeoPoint geoPoint = new GeoPoint(number(point, "latitude"), number(point, "longitude"));
                // Consecutive legs repeat the joining point; skip the duplicate.
                if (points.isEmpty() || !points.get(points.size() - 1).equals(geoPoint)) {
                    points.add(geoPoint);
                }
            }
        }

        List<IndexRange> tunnels = new ArrayList<>();
        List<IndexRange> closures = new ArrayList<>();
        JsonArray sections = route.getAsJsonArray("sections");
        if (sections != null) {
            for (JsonElement sectionElement : sections) {
                JsonObject section = sectionElement.getAsJsonObject();
                String type = text(section, "sectionType");
                IndexRange range = new IndexRange((int) number(section, "startPointIndex"),
                        (int) number(section, "endPointIndex"));
                if ("TUNNEL".equalsIgnoreCase(type)) {
                    tunnels.add(range);
                } else if ("TRAFFIC".equalsIgnoreCase(type)
                        && "ROAD_CLOSURE".equalsIgnoreCase(text(section, "simpleCategory"))) {
                    closures.add(range);
                }
            }
        }

        List<Maneuver> maneuvers = new ArrayList<>();
        JsonObject guidance = route.getAsJsonObject("guidance");
        if (guidance != null && guidance.getAsJsonArray("instructions") != null) {
            for (JsonElement instructionElement : guidance.getAsJsonArray("instructions")) {
                JsonObject instruction = instructionElement.getAsJsonObject();
                GeoPoint location = null;
                JsonObject point = instruction.getAsJsonObject("point");
                if (point != null) {
                    location = new GeoPoint(number(point, "latitude"), number(point, "longitude"));
                }
                maneuvers.add(new Maneuver(text(instruction, "message"), text(instruction, "street"), location,
                        number(instruction, "routeOffsetInMeters")));
            }
        }
        return new RouteCandidate(index, points, length, time, delay, trafficConsidered, tunnels, closures, maneuvers);
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
