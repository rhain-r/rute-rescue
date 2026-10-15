package com.rutarescue.persistence;

import com.rutarescue.exception.DuplicateRecordException;
import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.family.FamilyRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads and writes family_records.txt (Labs 5-7), now used for the local mirror of the
 * resident's record and for legacy import/export.
 *
 * Exception handling follows Lab 6:
 *  - missing file           -> empty list + notice (first run is normal)
 *  - non-numeric size       -> NumberFormatException, row skipped with line number
 *  - any other broken rule  -> InvalidFamilyRecordException, row skipped with line number
 *  - duplicate ID           -> row skipped, the first valid row is kept
 *  - read/write problems    -> IOException is thrown to the caller ("throws")
 */
public final class FamilyTextFileStore {

    public ImportReport load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (NoSuchFileException firstRun) {
            ImportReport empty = new ImportReport();
            empty.addNotice("No saved " + file.getFileName() + " yet; starting with an empty list.");
            return empty;
        }
    }

    /** Reads rows from any reader (a file, or a document chosen with the Android file picker). */
    public ImportReport read(BufferedReader reader) throws IOException {
        ImportReport report = new ImportReport();
        Set<String> seenIds = new HashSet<>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                line = line.substring(1); // ignore a UTF-8 byte-order mark
            }
            if (line.isBlank()) {
                continue;
            }
            try {
                FamilyRecord record = LegacyFamilyCodec.parseLine(line);
                String key = record.getRecordId().toUpperCase(Locale.ROOT);
                if (!seenIds.add(key)) {
                    report.addSkippedRow(lineNumber, "Duplicate family ID " + record.getRecordId()
                            + "; the first valid row was kept.");
                    continue;
                }
                report.addRecord(record);
            } catch (NumberFormatException badNumber) {
                report.addSkippedRow(lineNumber, "Family size is not a valid whole number. " + badNumber.getMessage());
            } catch (InvalidFamilyRecordException badRecord) {
                report.addSkippedRow(lineNumber, badRecord.getMessage());
            }
        }
        return report;
    }

    /**
     * Validates the whole batch first, then writes it atomically. If the batch is invalid
     * or the write fails, the existing file keeps its previous bytes.
     */
    public void save(Path file, List<FamilyRecord> records) throws IOException, DuplicateRecordException {
        validateBatch(records);
        AtomicFileWriter.writeText(file, writer -> write(writer, records));
    }

    /** Writes rows to any writer (used for export through the Android file picker). */
    public void write(Writer writer, List<FamilyRecord> records) throws IOException {
        for (FamilyRecord record : records) {
            writer.write(LegacyFamilyCodec.toLine(record));
            writer.write('\n');
        }
        writer.flush();
    }

    /** No nulls and no duplicate IDs (compared case-insensitively). */
    public static void validateBatch(List<FamilyRecord> records) throws DuplicateRecordException {
        if (records == null) {
            throw new IllegalArgumentException("The record list is required.");
        }
        Set<String> ids = new HashSet<>();
        for (FamilyRecord record : records) {
            if (record == null) {
                throw new IllegalArgumentException("The record list contains an empty entry.");
            }
            if (!ids.add(record.getRecordId().toUpperCase(Locale.ROOT))) {
                throw new DuplicateRecordException(record.getRecordId());
            }
        }
    }
}
