package com.rutarescue.persistence;

import com.rutarescue.exception.AtomicSaveException;
import com.rutarescue.exception.DuplicateRecordException;
import com.rutarescue.model.family.FamilyRecord;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Lab 6 persistence cases and Lab 7 T12-T15. Every test uses a temporary folder so the
 * sample data is never modified.
 */
public class FamilyTextFileStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final FamilyTextFileStore store = new FamilyTextFileStore();

    private static FamilyRecord record(String id, String name) throws Exception {
        return new FamilyRecord(id, name, "09171234567", "12 Sample St., Brgy. Malanday", "School Gym", 4);
    }

    @Test
    public void missingFileStartsWithEmptyList() throws Exception {
        ImportReport report = store.load(folder.getRoot().toPath().resolve("family_records.txt"));
        assertTrue(report.getRecords().isEmpty());
        assertTrue(report.getWarnings().get(0).contains("starting with an empty list"));
    }

    @Test
    public void malformedRowIsSkippedAndValidRowKept() throws Exception {
        Path file = write("FR-0001|Ana Cruz|09171234567|1 Road|Gym|3\n"
                + "FR-0002|Ben Cruz|09171234567|2 Road|Gym|many\n");
        ImportReport report = store.load(file);
        assertEquals(1, report.getRecords().size());
        assertEquals(1, report.getSkippedRows());
        assertTrue(report.getWarnings().get(0).startsWith("Line 2:"));
    }

    @Test
    public void t12_saveAndReloadRoundTrip() throws Exception {
        Path file = folder.getRoot().toPath().resolve("family_records.txt");
        FamilyRecord original = record(" fr-0002 ", "maria o'neill-santos");
        store.save(file, Arrays.asList(original));
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertEquals("FR-0002|Maria O'Neill-Santos|09171234567|12 Sample St., Brgy. Malanday|School Gym|4\n", text);
        FamilyRecord loaded = store.load(file).getRecords().get(0);
        assertEquals(original.toFileString(), loaded.toFileString());
    }

    @Test
    public void unicodeNamesSurviveRoundTrip() throws Exception {
        Path file = folder.getRoot().toPath().resolve("family_records.txt");
        store.save(file, Arrays.asList(record("FR-0003", "José Niño")));
        assertEquals("José Niño", store.load(file).getRecords().get(0).getHeadName());
    }

    @Test
    public void t14_duplicateBatchIsRejectedBeforeTouchingTheFile() throws Exception {
        Path file = folder.getRoot().toPath().resolve("family_records.txt");
        store.save(file, Arrays.asList(record("FR-0001", "Ana Cruz")));
        byte[] before = Files.readAllBytes(file);

        List<FamilyRecord> duplicates = Arrays.asList(record("FR-0001", "Ana Cruz"), record("fr-0001", "Ben Cruz"));
        assertThrows(DuplicateRecordException.class, () -> store.save(file, duplicates));
        assertThrows(IllegalArgumentException.class, () -> store.save(file, Arrays.asList(record("FR-0005", "Ana"), null)));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    public void t15_malformedLoadKeepsOnlyValidRows() throws Exception {
        Path file = write("FR-0001|Too|Few|Fields\n"
                + "FR-0002|Ben Cruz|09171234567|2 Road|Gym|many\n"
                + "FR-0003|Cara Cruz|09171234567|3 Road|Gym|101\n"
                + "fr-0004|dina cruz|0917-123-4567|4 Road|Gym|2\n"
                + "FR-0004|Duplicate Row|09171234567|4 Road|Gym|2\n");
        ImportReport report = store.load(file);
        assertEquals(1, report.getRecords().size());
        assertEquals(4, report.getSkippedRows());
        assertEquals("FR-0004|Dina Cruz|09171234567|4 Road|Gym|2", report.getRecords().get(0).toFileString());
    }

    @Test
    public void lab5SevenFieldRowIsStillAccepted() throws Exception {
        Path file = write("FR-0009|Ana Cruz|1 Road, Manila|09171234567|3|Ben Cruz 0918-765-4321|Covered Court\n");
        FamilyRecord record = store.load(file).getRecords().get(0);
        assertEquals("1 Road, Manila", record.getAddress());
        assertEquals("Covered Court", record.getPreferredCenter());
        assertEquals("09187654321", record.getTrustedContacts().get(0).getPhone());
    }

    @Test
    public void failedSaveKeepsTheOldFileAndTheInMemoryList() throws Exception {
        // A non-empty directory with the target's name makes the final replace fail.
        File blocker = folder.newFolder("family_records.txt");
        assertTrue(new File(blocker, "keep.txt").createNewFile());
        Path target = blocker.toPath();

        List<FamilyRecord> inMemory = new ArrayList<>(Arrays.asList(record("FR-0001", "Ana Cruz")));
        assertThrows(AtomicSaveException.class, () -> store.save(target, inMemory));

        assertEquals(1, inMemory.size());
        assertTrue(new File(blocker, "keep.txt").exists());
        String[] leftovers = folder.getRoot().list((dir, name) -> name.endsWith(".tmp"));
        assertFalse("temporary file must be cleaned up", leftovers != null && leftovers.length > 0);
    }

    private Path write(String content) throws Exception {
        Path file = folder.getRoot().toPath().resolve("input.txt");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
