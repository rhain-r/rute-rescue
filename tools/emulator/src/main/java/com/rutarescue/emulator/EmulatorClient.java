package com.rutarescue.emulator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Tiny REST client for the Firebase Local Emulator Suite (Auth + Firestore).
 *
 * The emulators accept the special token "owner", which bypasses the security rules (used
 * only to seed lgu_staff, exactly like an administrator in the Firebase console would).
 * Normal requests use the ID token of an emulator user, so the real rules are evaluated.
 */
public final class EmulatorClient {

    public static final String PROJECT = "demo-rutarescue";
    public static final String OWNER = "owner";

    private final String host;
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /** Result of one HTTP call: status code and JSON body. */
    public static final class Response {
        public final int status;
        public final JsonObject body;

        Response(int status, JsonObject body) {
            this.status = status;
            this.body = body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public boolean denied() {
            return status == 403;
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    public EmulatorClient(String host) {
        this.host = host;
    }

    public static EmulatorClient local() {
        return new EmulatorClient("127.0.0.1");
    }

    /** True when the Firestore and Auth emulators answer on their default ports. */
    public boolean isRunning() {
        try {
            call("GET", "http://" + host + ":8080/", null, null);
            call("GET", "http://" + host + ":9099/", null, null);
            return true;
        } catch (IOException notRunning) {
            return false;
        }
    }

    // ------------------------------------------------------------------ emulator housekeeping

    public void resetAll() throws IOException {
        call("DELETE", "http://" + host + ":8080/emulator/v1/projects/" + PROJECT + "/databases/(default)/documents", null, null);
        call("DELETE", "http://" + host + ":9099/emulator/v1/projects/" + PROJECT + "/accounts", null, null);
    }

    /** Creates an Auth emulator user and returns {uid, idToken}. */
    public String[] signUp(String email, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("returnSecureToken", true);
        Response response = call("POST", "http://" + host
                + ":9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-key", body, null);
        if (!response.ok()) throw new IOException("signUp failed: " + response);
        return new String[]{response.body.get("localId").getAsString(), response.body.get("idToken").getAsString()};
    }

    // ------------------------------------------------------------------ Firestore documents

    private String documents() {
        return "http://" + host + ":8080/v1/projects/" + PROJECT + "/databases/(default)/documents";
    }

    private static String name(String path) {
        return "projects/" + PROJECT + "/databases/(default)/documents/" + path;
    }

    public Response get(String path, String token) throws IOException {
        return call("GET", documents() + "/" + path, null, token);
    }

    /** Creates or replaces a document (like DocumentReference.set). */
    public Response set(String path, Map<String, Object> data, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.add("fields", FirestoreValues.encodeFields(data));
        return call("PATCH", documents() + "/" + path, body, token);
    }

    public Response delete(String path, String token) throws IOException {
        return call("DELETE", documents() + "/" + path, null, token);
    }

    /** Atomic batch of set() writes, like WriteBatch.commit(). Keys are document paths. */
    public Response commit(Map<String, Map<String, Object>> writes, String token) throws IOException {
        JsonArray array = new JsonArray();
        for (Map.Entry<String, Map<String, Object>> write : writes.entrySet()) {
            JsonObject update = new JsonObject();
            update.addProperty("name", name(write.getKey()));
            update.add("fields", FirestoreValues.encodeFields(write.getValue()));
            JsonObject item = new JsonObject();
            item.add("update", update);
            array.add(item);
        }
        JsonObject body = new JsonObject();
        body.add("writes", array);
        return call("POST", documents() + ":commit", body, token);
    }

    /** Runs a single-field equality query, like collection().whereEqualTo(field, value). */
    public Response queryEquals(String collection, String field, Object value, String token) throws IOException {
        JsonObject fieldRef = new JsonObject();
        fieldRef.addProperty("fieldPath", field);
        JsonObject fieldFilter = new JsonObject();
        fieldFilter.add("field", fieldRef);
        fieldFilter.addProperty("op", "EQUAL");
        fieldFilter.add("value", FirestoreValues.encode(value));
        JsonObject where = new JsonObject();
        where.add("fieldFilter", fieldFilter);
        JsonObject from = new JsonObject();
        from.addProperty("collectionId", collection);
        JsonArray fromArray = new JsonArray();
        fromArray.add(from);
        JsonObject structured = new JsonObject();
        structured.add("from", fromArray);
        structured.add("where", where);
        JsonObject body = new JsonObject();
        body.add("structuredQuery", structured);
        return call("POST", documents() + ":runQuery", body, token);
    }

    /** Number of documents returned by a runQuery call (0 when denied). */
    public static int documentCount(Response response) {
        if (!response.ok() || !response.body.has("items")) return 0;
        int count = 0;
        for (JsonElement element : response.body.getAsJsonArray("items")) {
            if (element.isJsonObject() && element.getAsJsonObject().has("document")) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ HTTP

    private Response call(String method, String url, JsonObject body, String token) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(gson.toJson(body));
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, publisher);
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", interrupted);
        }
        JsonObject json = new JsonObject();
        String text = response.body();
        if (text != null && !text.isBlank() && (text.startsWith("{") || text.startsWith("["))) {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject()) json = parsed.getAsJsonObject(); else json.add("items", parsed);
        }
        return new Response(response.statusCode(), json);
    }
}
