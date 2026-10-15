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

/**
 * Destination search with OpenStreetMap's Nominatim geocoder: free, no key, no payment.
 * Results are limited to a box around Metro Manila.
 *
 * Nominatim usage policy, which this class follows:
 *   - at most one request per second (enforced below),
 *   - a descriptive User-Agent (HttpJsonClient.USER_AGENT),
 *   - no search-as-you-type: allowsSearchAsYouType() returns false, so the app only calls it
 *     when the user presses Search,
 *   - attribution: © OpenStreetMap contributors (About screen and map).
 */
public final class NominatimSearchProvider implements PlaceSearchProvider {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org/search?format=jsonv2";
    private static final String METRO_MANILA_BOX = "&viewbox=120.88,14.80,121.16,14.33&bounded=1&countrycodes=ph";
    private static final long MIN_INTERVAL_MS = 1100;

    private final HttpJsonClient http;
    private long lastRequestAt;

    public NominatimSearchProvider(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public List<PlaceResult> search(String query, GeoPoint near) throws OfflineException, ProviderException {
        String trimmed = query == null ? "" : query.strip();
        if (trimmed.length() < 3) return new ArrayList<>();
        waitForRateLimit();
        String url = BASE_URL + "&q=" + encode(trimmed) + "&limit=8&accept-language=en" + METRO_MANILA_BOX;
        return parse(http.get(url));
    }

    @Override
    public boolean allowsSearchAsYouType() {
        return false;
    }

    /** One request per second at most (Nominatim policy). Runs on a background thread. */
    private synchronized void waitForRateLimit() {
        long wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    /** Parses Nominatim's jsonv2 answer: [{name, display_name, lat, lon}, ...]. */
    static List<PlaceResult> parse(String json) throws ProviderException {
        try {
            List<PlaceResult> places = new ArrayList<>();
            JsonArray results = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : results) {
                JsonObject result = element.getAsJsonObject();
                if (!result.has("lat") || !result.has("lon")) continue;
                String address = result.has("display_name") ? result.get("display_name").getAsString() : "";
                String name = result.has("name") && !result.get("name").isJsonNull() ? result.get("name").getAsString() : "";
                if (name.isEmpty()) {
                    int comma = address.indexOf(',');
                    name = comma > 0 ? address.substring(0, comma) : address;
                }
                places.add(new PlaceResult(name, address,
                        new GeoPoint(result.get("lat").getAsDouble(), result.get("lon").getAsDouble())));
            }
            return places;
        } catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException e) {
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
