package com.rutarescue.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Lab 6 binary system-state cases. */
public class SystemStateStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SystemStateStore store = new SystemStateStore();

    @Test
    public void binaryRoundTrip() throws Exception {
        Path file = folder.getRoot().toPath().resolve("system.dat");
        SystemState saved = new SystemState(3, 3, "1.1", true);
        store.save(file, saved);
        SystemStateStore.LoadResult loaded = store.load(file);
        assertFalse(loaded.usedDefaults());
        assertEquals(saved, loaded.getState());
    }

    @Test
    public void missingFileUsesSafeDefaults() {
        SystemStateStore.LoadResult loaded = store.load(folder.getRoot().toPath().resolve("system.dat"));
        assertTrue(loaded.usedDefaults());
        assertEquals(SystemState.defaults(), loaded.getState());
    }

    @Test
    public void truncatedFileUsesSafeDefaults() throws Exception {
        Path file = folder.getRoot().toPath().resolve("system.dat");
        Files.write(file, new byte[]{0, 0, 0, 3, 0, 0});
        SystemStateStore.LoadResult loaded = store.load(file);
        assertTrue(loaded.usedDefaults());
        assertTrue(loaded.getMessage().contains("truncated"));
    }

    @Test
    public void recordIdsContinueAfterRestart() throws Exception {
        Path file = folder.getRoot().toPath().resolve("system.dat");
        SystemState state = SystemState.defaults();
        assertEquals("FR-0001", state.nextRecordId("FR"));
        store.save(file, state);
        SystemState reloaded = store.load(file).getState();
        assertEquals("FR-0002", reloaded.nextRecordId("FR"));
    }
}
