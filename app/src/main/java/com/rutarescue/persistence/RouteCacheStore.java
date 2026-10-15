package com.rutarescue.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rutarescue.exception.AtomicSaveException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Saves recent routing answers as small JSON files so they can be re-opened offline
 * ("reopening cached routes", DOCX section 11). Damaged files are skipped, not fatal.
 */
public final class RouteCacheStore {

    public static final int MAX_ENTRIES = 10;
    private static final String SUFFIX = ".route.json";

    private final Path directory;
    private final Gson gson = new Gson();

    public RouteCacheStore(Path directory) {
        this.directory = directory;
    }

    public synchronized CachedRouteEntry save(CachedRouteEntry entry) throws AtomicSaveException {
        if (entry.id == null || entry.id.isBlank()) {
            entry.id = UUID.randomUUID().toString();
        }
        String json = gson.toJson(entry);
        AtomicFileWriter.writeText(directory.resolve(entry.id + SUFFIX), writer -> writer.write(json));
        prune();
        return entry;
    }

    /** Newest first. Unreadable files are ignored. */
    public synchronized List<CachedRouteEntry> list() {
        List<CachedRouteEntry> entries = new ArrayList<>();
        if (!Files.isDirectory(directory)) return entries;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path file : files) {
                CachedRouteEntry entry = readQuietly(file);
                if (entry != null) entries.add(entry);
            }
        } catch (IOException ignored) {
            // An unreadable cache folder simply means "no cached routes".
        }
        entries.sort(Comparator.comparingLong((CachedRouteEntry e) -> e.fetchedAt).reversed());
        return entries;
    }

    /** Deletes every cached route (at sign-out: routes start at the user's own location). */
    public synchronized void clear() {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path file : files) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Anything left behind is pruned by later saves.
        }
    }

    public synchronized CachedRouteEntry find(String id) {
        if (id == null) return null;
        return readQuietly(directory.resolve(id + SUFFIX));
    }

    private CachedRouteEntry readQuietly(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CachedRouteEntry entry = gson.fromJson(reader, CachedRouteEntry.class);
            return entry != null && entry.rawProviderJson != null ? entry : null;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    private void prune() {
        List<CachedRouteEntry> entries = list();
        for (int i = MAX_ENTRIES; i < entries.size(); i++) {
            try {
                Files.deleteIfExists(directory.resolve(entries.get(i).id + SUFFIX));
            } catch (IOException ignored) {
                // Keeping an extra cached file is harmless.
            }
        }
    }
}
