package com.rutarescue.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Offline route cache: newest first, damaged files skipped, and emptied at sign-out. */
public class RouteCacheStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private CachedRouteEntry entry(String destination, long fetchedAt) {
        CachedRouteEntry entry = new CachedRouteEntry();
        entry.destinationName = destination;
        entry.originLat = 14.5995;
        entry.originLon = 120.9842;
        entry.fetchedAt = fetchedAt;
        entry.rawProviderJson = "{}";
        return entry;
    }

    @Test
    public void listsNewestFirstAndSkipsDamagedFiles() throws Exception {
        Path directory = folder.getRoot().toPath();
        RouteCacheStore store = new RouteCacheStore(directory);
        store.save(entry("Older center", 1000));
        store.save(entry("Newer center", 2000));
        Files.write(directory.resolve("broken.route.json"), "not json".getBytes());

        assertEquals(2, store.list().size());
        assertEquals("Newer center", store.list().get(0).destinationName);
    }

    @Test
    public void clearRemovesEveryCachedRoute() throws Exception {
        Path directory = folder.getRoot().toPath();
        RouteCacheStore store = new RouteCacheStore(directory);
        CachedRouteEntry saved = store.save(entry("Almario Elementary School", 1000));
        Files.write(directory.resolve("broken.route.json"), "not json".getBytes());

        store.clear();

        assertTrue(store.list().isEmpty());
        assertNull(store.find(saved.id));
        assertTrue("damaged files are removed too", Files.notExists(directory.resolve("broken.route.json")));
    }

    @Test
    public void clearOnAMissingFolderDoesNothing() {
        new RouteCacheStore(folder.getRoot().toPath().resolve("never-created")).clear();
    }
}
