package com.rutarescue.persistence;

import com.rutarescue.exception.AtomicSaveException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Binary persistence of system.dat with DataOutputStream/DataInputStream (Lab 5).
 * The values must be read back in exactly the order and types they were written:
 *     int  lastGeneratedRecordNumber
 *     int  totalRecordsSaved
 *     UTF  applicationVersion
 *     bool autoSaveEnabled
 * TEAM NOTE: confirm against your Lab 6 SystemState.java; if the version was written with
 * writeDouble, change the two marked lines below.
 */
public final class SystemStateStore {

    /** Result of loading: the state plus a message when safe defaults had to be used. */
    public static final class LoadResult {
        private final SystemState state;
        private final boolean usedDefaults;
        private final String message;

        LoadResult(SystemState state, boolean usedDefaults, String message) {
            this.state = state;
            this.usedDefaults = usedDefaults;
            this.message = message;
        }

        public SystemState getState() { return state; }
        public boolean usedDefaults() { return usedDefaults; }
        public String getMessage() { return message; }
    }

    public LoadResult load(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int lastRecord = in.readInt();
            int totalSaved = in.readInt();
            String version = in.readUTF();          // (marked) writeUTF / readUTF
            boolean autoSave = in.readBoolean();
            return new LoadResult(new SystemState(lastRecord, totalSaved, version, autoSave), false, "");
        } catch (NoSuchFileException firstRun) {
            return new LoadResult(SystemState.defaults(), true, "No system.dat yet; using safe defaults.");
        } catch (EOFException truncated) {
            return new LoadResult(SystemState.defaults(), true, "system.dat ended early (truncated); using safe defaults.");
        } catch (IOException | IllegalArgumentException damaged) {
            return new LoadResult(SystemState.defaults(), true,
                    "system.dat could not be read (" + damaged.getMessage() + "); using safe defaults.");
        }
    }

    public void save(Path file, SystemState state) throws AtomicSaveException {
        AtomicFileWriter.writeBinary(file, out -> {
            out.writeInt(state.getLastGeneratedRecordNumber());
            out.writeInt(state.getTotalRecordsSaved());
            out.writeUTF(state.getApplicationVersion());  // (marked) writeUTF / readUTF
            out.writeBoolean(state.isAutoSaveEnabled());
        });
    }
}
