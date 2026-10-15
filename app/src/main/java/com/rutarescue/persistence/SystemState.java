package com.rutarescue.persistence;

import java.util.Locale;

/**
 * Internal program state stored in system.dat (Lab 5): the last generated record number,
 * total records saved, application version and auto-save setting. The record number lets
 * the app keep generating new family IDs (FR-0001, FR-0002, ...) after a restart.
 */
public final class SystemState {

    public static final String CURRENT_APP_VERSION = "1.0";
    public static final String DEFAULT_ID_PREFIX = "FR";

    private int lastGeneratedRecordNumber;
    private int totalRecordsSaved;
    private String applicationVersion;
    private boolean autoSaveEnabled;

    public SystemState(int lastGeneratedRecordNumber, int totalRecordsSaved, String applicationVersion,
                       boolean autoSaveEnabled) {
        if (lastGeneratedRecordNumber < 0 || totalRecordsSaved < 0) {
            throw new IllegalArgumentException("Counters in system.dat cannot be negative.");
        }
        if (applicationVersion == null || applicationVersion.isBlank()) {
            throw new IllegalArgumentException("Application version is required.");
        }
        this.lastGeneratedRecordNumber = lastGeneratedRecordNumber;
        this.totalRecordsSaved = totalRecordsSaved;
        this.applicationVersion = applicationVersion.strip();
        this.autoSaveEnabled = autoSaveEnabled;
    }

    /** Safe defaults used on first run or when system.dat is damaged. */
    public static SystemState defaults() {
        return new SystemState(0, 0, CURRENT_APP_VERSION, true);
    }

    /** Generates the next family ID, e.g. FR-0004, and remembers the new number. */
    public String nextRecordId(String prefix) {
        if (lastGeneratedRecordNumber >= 9999) {
            throw new IllegalStateException("No more 4-digit family IDs are available.");
        }
        lastGeneratedRecordNumber++;
        return String.format(Locale.ROOT, "%s-%04d", prefix, lastGeneratedRecordNumber);
    }

    /** Makes sure an imported ID number is never generated again. */
    public void reserveRecordNumber(int number) {
        if (number > lastGeneratedRecordNumber) {
            lastGeneratedRecordNumber = Math.min(number, 9999);
        }
    }

    public void recordSaved(int recordsInFile) {
        totalRecordsSaved = Math.max(0, recordsInFile);
        applicationVersion = CURRENT_APP_VERSION;
    }

    public int getLastGeneratedRecordNumber() { return lastGeneratedRecordNumber; }
    public int getTotalRecordsSaved() { return totalRecordsSaved; }
    public String getApplicationVersion() { return applicationVersion; }
    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }

    public void setAutoSaveEnabled(boolean autoSaveEnabled) {
        this.autoSaveEnabled = autoSaveEnabled;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SystemState)) return false;
        SystemState that = (SystemState) other;
        return lastGeneratedRecordNumber == that.lastGeneratedRecordNumber
                && totalRecordsSaved == that.totalRecordsSaved
                && applicationVersion.equals(that.applicationVersion)
                && autoSaveEnabled == that.autoSaveEnabled;
    }

    @Override
    public int hashCode() {
        return lastGeneratedRecordNumber * 31 + totalRecordsSaved * 17 + applicationVersion.hashCode()
                + (autoSaveEnabled ? 1 : 0);
    }

    @Override
    public String toString() {
        return "last record #" + lastGeneratedRecordNumber + ", saved " + totalRecordsSaved
                + ", version " + applicationVersion + ", auto-save " + (autoSaveEnabled ? "on" : "off");
    }
}
