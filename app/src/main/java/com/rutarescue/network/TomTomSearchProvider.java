package com.rutarescue.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.place.PlaceResult;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TomTom Search API (fuzzy search), limited to a box around Metro Manila. Replaces the
 * legacy Geoapify autocomplete, because the DOCX names TomTom for geocoding.
 */
public final class TomTomSearchProvider implements PlaceSearchProvider {

    private static final String BASE_URL = "https://api.tomtom.com/search/2/search/";
    private static final String METRO_MANILA_BOX = "&topLeft=14.80,120.88&btmRight=14.33,121.16";

    private final HttpJsonClient http;
    private final String apiKey;

    public TomTomSearchProvider(HttpJsonClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public List<PlaceResult> search(String query, GeoPoint near) throws OfflineException, ProviderException {
        if (apiKey.isEmpty()) {
            throw new ProviderException("No TomTom API key is configured (TOMTOM_API_KEY in local.properties).", -1);
        }
        String trimmed = query == null ? "" : query.strip();
        if (trimmed.length() < 2) {
            return new ArrayList<>();
        }
        String url = BASE_URL + encode(trimmed) + ".json?key=" + apiKey
                + "&limit=8&countrySet=PH&typeahead=true&language=en-GB" + METRO_MANILA_BOX
                + (near == null ? "" : String.format(Locale.ROOT, "&lat=%.5f&lon=%.5f",
                near.getLatitude(), near.getLongitude()));
        return parse(http.get(url));
    }

    @Override
    public boolean allowsSearchAsYouType() {
        return true;
    }

    static List<PlaceResult> parse(String json) throws ProviderException {
        try {
            List<PlaceResult> places = new ArrayList<>();
            JsonArray results = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("results");
            if (results == null) return places;
            for (JsonElement element : results) {
                JsonObject result = element.getAsJsonObject();
                JsonObject position = result.getAsJsonObject("position");
                if (position == null) continue;
                String name = "";
                if (result.has("poi") && result.getAsJsonObject("poi").has("name")) {
                    name = result.getAsJsonObject("poi").get("name").getAsString();
                }
                String address = result.has("address") && result.getAsJsonObject("address").has("freeformAddress")
                        ? result.getAsJsonObject("address").get("freeformAddress").getAsString() : "";
                places.add(new PlaceResult(name.isEmpty() ? address : name, address,
                        new GeoPoint(position.get("lat").getAsDouble(), position.get("lon").getAsDouble())));
            }
            return places;
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            throw new ProviderException("The search answer could not be read.", -1, e);
        }
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
