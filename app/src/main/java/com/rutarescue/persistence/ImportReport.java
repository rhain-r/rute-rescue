package com.rutarescue.persistence;

import com.rutarescue.model.family.FamilyRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of reading a family_records.txt file: the valid records, plus a warning for every
 * row that was skipped (with its line number), so one bad row never rejects the whole file.
 */
public final class ImportReport {

    private final List<FamilyRecord> records = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int skippedRows;

    void addRecord(FamilyRecord record) {
        records.add(record);
    }

    void addSkippedRow(int lineNumber, String message) {
        skippedRows++;
        warnings.add("Line " + lineNumber + ": " + message);
    }

    void addNotice(String message) {
        warnings.add(message);
    }

    public List<FamilyRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public String summary() {
        return "Loaded " + records.size() + " family record(s); skipped " + skippedRows + " row(s).";
    }
}
