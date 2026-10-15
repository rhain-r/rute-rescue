package com.rutarescue.network;

import com.rutarescue.exception.OfflineException;
import com.rutarescue.exception.ProviderException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTPS GET client built on HttpURLConnection and character streams.
 * Must be called from a background thread (Android forbids network on the main thread).
 * URLs are never logged because they contain the API key.
 */
public class HttpJsonClient {

    /**
     * OpenStreetMap services (Nominatim, OSRM, tiles) require every app to identify itself with
     * a descriptive User-Agent. No personal data is included.
     */
    public static final String USER_AGENT = "RutaRescue/1.0 (Android; student flood-preparedness project)";

    private final int timeoutMillis;

    public HttpJsonClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String get(String url) throws OfflineException, ProviderException {
        return request(url, null);
    }

    /** POSTs a JSON body (TomTom detours use this for "avoidAreas"). */
    public String post(String url, String jsonBody) throws OfflineException, ProviderException {
        return request(url, jsonBody == null ? "" : jsonBody);
    }

    /** GET when jsonBody is null, otherwise POST with the JSON body. */
    private String request(String url, String jsonBody) throws OfflineException, ProviderException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (jsonBody != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (status < 200 || status >= 300) {
                throw new ProviderException(describeStatus(status), status);
            }
            return body;
        } catch (UnknownHostException | ConnectException | SocketTimeoutException noConnection) {
            throw new OfflineException("Could not reach the online service. Check your internet connection.", noConnection);
        } catch (IOException ioProblem) {
            throw new ProviderException("Network error while contacting the service.", -1, ioProblem);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Reads a whole response body using try-with-resources. */
    static String readAll(InputStream stream) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                out.append(buffer, 0, read);
            }
        }
        return out.toString();
    }

    private static String describeStatus(int status) {
        if (status == 401 || status == 403) {
            return "The service rejected the API key (HTTP " + status + "). Check TOMTOM_API_KEY in local.properties.";
        }
        if (status == 429) {
            return "The free daily request limit was reached (HTTP 429). Try again later.";
        }
        if (status == 400) {
            return "The service could not plan this trip (HTTP 400).";
        }
        return "The service returned an error (HTTP " + status + ").";
    }
}
