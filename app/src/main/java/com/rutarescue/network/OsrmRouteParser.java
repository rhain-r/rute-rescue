package com.rutarescue.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.route.Maneuver;
import com.rutarescue.model.route.RouteCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses an OSRM "route" answer (OpenStreetMap routing) into RouteCandidate objects, the
 * same objects the TomTom parser produces, so the bridge-aware RiskEngine assesses both alike.
 * Kept separate from the HTTP code so it can be unit-tested with a saved JSON string.
 *
 * Fields used (request with overview=full, geometries=geojson, steps=true):
 *   code                                   "Ok", or an error code such as "NoRoute"
 *   routes[].distance / routes[].duration   metres / seconds (typical speeds, no live traffic)
 *   routes[].geometry.coordinates           [[lon, lat], ...]
 *   routes[].legs[].steps[]                 name, distance, maneuver {type, modifier, location}
 *
 * OSRM does not report tunnels or closures as route sections; the RiskEngine still finds
 * OpenStreetMap bridges and tunnels through the prepared StructureIndex, and LGU closures
 * through the closure list, so nothing is lost.
 */
public final class OsrmRouteParser {

    private OsrmRouteParser() {
    }

    public static List<RouteCandidate> parse(String json) throws ProviderException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String code = text(root, "code");
            if (!"Ok".equalsIgnoreCase(code)) {
                throw new ProviderException("NoRoute".equalsIgnoreCase(code) || "NoSegment".equalsIgnoreCase(code)
                        ? "No route was found between these points." : "The routing service could not plan this trip.", -1);
            }
            JsonArray routes = root.getAsJsonArray("routes");
            if (routes == null || routes.size() == 0) {
                throw new ProviderException("No route was found between these points.", -1);
            }
            List<RouteCandidate> candidates = new ArrayList<>();
            for (int index = 0; index < routes.size(); index++) {
                candidates.add(parseRoute(routes.get(index).getAsJsonObject(), index));
            }
            return candidates;
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            throw new ProviderException("The routing answer could not be read.", -1, e);
        }
    }

    private static RouteCandidate parseRoute(JsonObject route, int index) {
        List<GeoPoint> points = new ArrayList<>();
        for (JsonElement element : route.getAsJsonObject("geometry").getAsJsonArray("coordinates")) {
            JsonArray pair = element.getAsJsonArray();
            GeoPoint point = new GeoPoint(pair.get(1).getAsDouble(), pair.get(0).getAsDouble());
            if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) points.add(point);
        }

        List<Maneuver> maneuvers = new ArrayList<>();
        double offset = 0;
        JsonArray legs = route.getAsJsonArray("legs");
        if (legs != null) {
            for (JsonElement legElement : legs) {
                JsonArray steps = legElement.getAsJsonObject().getAsJsonArray("steps");
                if (steps == null) continue;
                for (JsonElement stepElement : steps) {
                    JsonObject step = stepElement.getAsJsonObject();
                    JsonObject maneuver = step.getAsJsonObject("maneuver");
                    String street = text(step, "name");
                    GeoPoint location = null;
                    JsonArray at = maneuver == null ? null : maneuver.getAsJsonArray("location");
                    if (at != null && at.size() == 2) location = new GeoPoint(at.get(1).getAsDouble(), at.get(0).getAsDouble());
                    maneuvers.add(new Maneuver(instruction(maneuver, street), street, location, offset));
                    offset += number(step, "distance");
                }
            }
        }
        // OSRM times use typical speeds: no traffic delay, and traffic is NOT considered.
        return new RouteCandidate(index, points, number(route, "distance"), Math.round(number(route, "duration")),
                0, false, Collections.emptyList(), Collections.emptyList(), maneuvers);
    }

    /** Builds "Turn left onto Rizal Avenue"-style text from an OSRM maneuver. */
    static String instruction(JsonObject maneuver, String street) {
        String type = maneuver == null ? "" : text(maneuver, "type");
        String modifier = maneuver == null ? "" : text(maneuver, "modifier").replace('_', ' ');
        String onto = street.isEmpty() ? "" : " onto " + street;
        switch (type) {
            case "depart":
                return street.isEmpty() ? "Start walking or driving" : "Head out on " + street;
            case "arrive":
                return "Arrive at your destination";
            case "turn":
            case "end of road":
            case "fork":
            case "on ramp":
            case "off ramp":
                return modifier.isEmpty() ? "Turn" + onto : "Turn " + modifier + onto;
            case "roundabout":
            case "rotary":
                return "Enter the roundabout" + (street.isEmpty() ? "" : " and take " + street);
            case "merge":
                return "Merge" + onto;
            case "new name":
            case "continue":
            default:
                return street.isEmpty() ? "Continue" + (modifier.isEmpty() ? "" : " " + modifier)
                        : "Continue on " + street;
        }
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
