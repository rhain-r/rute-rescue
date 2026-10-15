package com.rutarescue.persistence;

import com.rutarescue.exception.AtomicSaveException;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Safe saving (Lab 7 / DOCX section 9): write everything to a temporary sibling file, then
 * replace the real file in one step. If anything fails, the old file is untouched and the
 * temporary file is removed.
 */
public final class AtomicFileWriter {

    /** Writes text content (used for family_records.txt and cached routes). */
    public interface TextContent {
        void writeTo(Writer writer) throws IOException;
    }

    /** Writes binary content (used for system.dat). */
    public interface BinaryContent {
        void writeTo(DataOutputStream out) throws IOException;
    }

    private AtomicFileWriter() {
    }

    public static void writeText(Path target, TextContent content) throws AtomicSaveException {
        Path temp = null;
        try {
            temp = createTempSibling(target);
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                content.writeTo(writer);
            }
            replace(temp, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new AtomicSaveException("Could not save " + target.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static void writeBinary(Path target, BinaryContent content) throws AtomicSaveException {
        Path temp = null;
        try {
            temp = createTempSibling(target);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                content.writeTo(out);
            }
            replace(temp, target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw new AtomicSaveException("Could not save " + target.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static Path createTempSibling(Path target) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        return Files.createTempFile(directory, target.getFileName().toString() + ".", ".tmp");
    }

    private static void replace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            // Fallback: still a replacement, but without protection against power loss.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing else we can do; the original file is still safe.
        }
    }
}
