package com.rutarescue.service;

import com.rutarescue.exception.DuplicateRecordException;
import com.rutarescue.model.common.DataFreshness;
import com.rutarescue.model.common.SourcedData;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.persistence.FamilyTextFileStore;
import com.rutarescue.persistence.ImportReport;
import com.rutarescue.persistence.SystemState;
import com.rutarescue.persistence.SystemStateStore;
import com.rutarescue.repository.Callback;
import com.rutarescue.repository.FamilyRepository;
import com.rutarescue.util.AppExecutors;
import com.rutarescue.util.SafeLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Family record use cases (DOCX section 4 "My Record" and section 9).
 *
 * The primary copy is in Firestore (with its offline cache). Every successful save is also
 * mirrored to the Lab formats on the phone: data/family_records.txt (text) and
 * data/system.dat (binary), and residents can import/export the text format.
 */
public final class FamilyService {

    private static final String TAG = "RR-Family";
    private static final Pattern ID_NUMBER = Pattern.compile("[A-Za-z]+-([0-9]{4})");

    /** Snapshot of the two Lab files for the "Lab data files" screen. */
    public static final class LocalFilesStatus {
        public final ImportReport textReport;
        public final SystemState systemState;
        public final String systemMessage;
        public final String familyFilePath;
        public final String systemFilePath;
        public final String lastMirrorError;

        LocalFilesStatus(ImportReport textReport, SystemState systemState, String systemMessage,
                         String familyFilePath, String systemFilePath, String lastMirrorError) {
            this.textReport = textReport;
            this.systemState = systemState;
            this.systemMessage = systemMessage;
            this.familyFilePath = familyFilePath;
            this.systemFilePath = systemFilePath;
            this.lastMirrorError = lastMirrorError;
        }
    }

    private final FamilyRepository repository;
    private final AppExecutors executors;
    private final Path familyFile;
    private final Path systemFile;
    private final FamilyTextFileStore textStore = new FamilyTextFileStore();
    private final SystemStateStore stateStore = new SystemStateStore();
    private volatile String lastMirrorError = "";

    public FamilyService(FamilyRepository repository, AppExecutors executors, Path dataDirectory) {
        this.repository = repository;
        this.executors = executors;
        this.familyFile = dataDirectory.resolve("family_records.txt");
        this.systemFile = dataDirectory.resolve("system.dat");
    }

    public void load(String ownerUid, Callback<SourcedData<FamilyRecord>> callback) {
        repository.load(ownerUid, new Callback<SourcedData<FamilyRecord>>() {
            @Override
            public void onSuccess(SourcedData<FamilyRecord> result) {
                // Update the Lab file first, so a screen that re-reads it afterwards sees the new state.
                executors.io().execute(() -> {
                    keepLabFileForSignedInResident(result);
                    post(callback, result, null);
                });
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /**
     * family_records.txt must only ever hold the signed-in resident's record, so that a shared
     * phone never shows another family's name, phone or address. A missing record is only
     * trusted when the server confirmed it (offline, we keep the file as it is). Runs on the io thread.
     */
    private void keepLabFileForSignedInResident(SourcedData<FamilyRecord> result) {
        FamilyRecord record = result.getData();
        if (record == null && result.getFreshness() != DataFreshness.LIVE) return;
        try {
            if (record == null) {
                Files.deleteIfExists(familyFile);
            } else {
                textStore.save(familyFile, Collections.singletonList(record));
            }
        } catch (IOException | DuplicateRecordException e) {
            SafeLog.w(TAG, "Could not update the local family file", e);
        }
    }

    /** At sign-out: removes the family's personal details from the phone. system.dat only holds counters. */
    public void clearLocalCopy() {
        executors.io().execute(() -> {
            try {
                Files.deleteIfExists(familyFile);
            } catch (IOException e) {
                SafeLog.w(TAG, "Could not delete the local family file", e);
            }
        });
    }

    /** Generates the next family ID (FR-0001, ...) using the counter stored in system.dat. */
    public void nextRecordId(Callback<String> callback) {
        executors.io().execute(() -> {
            try {
                SystemState state = stateStore.load(systemFile).getState();
                String id = state.nextRecordId(SystemState.DEFAULT_ID_PREFIX);
                stateStore.save(systemFile, state);
                post(callback, id, null);
            } catch (IOException | RuntimeException e) {
                post(callback, null, e);
            }
        });
    }

    /** Saves to Firestore, then mirrors the record into the Lab text/binary files. */
    public void save(FamilyRecord record, Callback<Void> callback) {
        repository.save(record, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                // Mirror first, so a screen that re-reads the Lab files afterwards sees this record.
                executors.io().execute(() -> {
                    mirrorToLabFiles(record);
                    post(callback, null, null);
                });
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /** Runs on the io thread. */
    private void mirrorToLabFiles(FamilyRecord record) {
        try {
            textStore.save(familyFile, Collections.singletonList(record));
            SystemStateStore.LoadResult loaded = stateStore.load(systemFile);
            SystemState state = loaded.getState();
            reserveIdNumber(state, record.getRecordId());
            state.recordSaved(1);
            stateStore.save(systemFile, state);
            lastMirrorError = "";
        } catch (IOException | DuplicateRecordException e) {
            // The Firestore copy and the in-memory record are still fine; we only report it.
            lastMirrorError = e.getMessage();
            SafeLog.w(TAG, "Could not mirror the record to the Lab files", e);
        }
    }

    /** Reads a family_records.txt chosen by the user (Android file picker stream). */
    public void importLegacy(InputStream stream, Callback<ImportReport> callback) {
        executors.io().execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                ImportReport report = textStore.read(reader);
                SystemState state = stateStore.load(systemFile).getState();
                for (FamilyRecord imported : report.getRecords()) reserveIdNumber(state, imported.getRecordId());
                stateStore.save(systemFile, state);
                post(callback, report, null);
            } catch (IOException e) {
                post(callback, null, e);
            }
        });
    }

    /** Writes the record as one family_records.txt row to a document chosen by the user. */
    public void exportLegacy(OutputStream stream, FamilyRecord record, Callback<Void> callback) {
        executors.io().execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                textStore.write(writer, Collections.singletonList(record));
                post(callback, null, null);
            } catch (IOException e) {
                post(callback, null, e);
            }
        });
    }

    /** Re-reads both Lab files from disk (proves persistence after a restart). */
    public void readLocalFiles(Callback<LocalFilesStatus> callback) {
        executors.io().execute(() -> {
            try {
                ImportReport report = textStore.load(familyFile);
                SystemStateStore.LoadResult state = stateStore.load(systemFile);
                post(callback, new LocalFilesStatus(report, state.getState(), state.getMessage(),
                        familyFile.toString(), systemFile.toString(), lastMirrorError), null);
            } catch (IOException e) {
                post(callback, null, e);
            }
        });
    }

    /** Takes over an imported legacy record as the signed-in resident's record. */
    public FamilyRecord adoptImported(FamilyRecord imported, UserProfile owner) {
        imported.assignOwner(owner.getUid(), owner.getCity());
        return imported;
    }

    private static void reserveIdNumber(SystemState state, String recordId) {
        Matcher matcher = ID_NUMBER.matcher(recordId);
        if (matcher.matches()) state.reserveRecordNumber(Integer.parseInt(matcher.group(1)));
    }

    private <T> void post(Callback<T> callback, T value, Exception error) {
        executors.main().execute(() -> {
            if (error == null) callback.onSuccess(value); else callback.onError(error);
        });
    }
}
