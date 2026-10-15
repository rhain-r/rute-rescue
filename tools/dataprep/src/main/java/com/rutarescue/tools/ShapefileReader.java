package com.rutarescue.tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal ESRI shapefile reader for POLYGON (type 5) layers plus their DBF attributes.
 * The header uses big-endian integers and the record contents use little-endian values,
 * so ByteBuffer.order() is switched while reading.
 */
final class ShapefileReader {

    static final int FILE_CODE = 9994;
    static final int POLYGON = 5;

    /** Parsed header of the .shp file. */
    static final class Header {
        int shapeType;
        double minX, minY, maxX, maxY;
        long fileLengthBytes;
    }

    /** One DBF field definition. */
    static final class DbfField {
        final String name;
        final char type;
        final int length;

        DbfField(String name, char type, int length) {
            this.name = name;
            this.type = type;
            this.length = length;
        }
    }

    private ShapefileReader() {
    }

    static Header readHeader(Path shp) throws IOException {
        byte[] bytes = new byte[100];
        try (java.io.InputStream in = Files.newInputStream(shp)) {
            int read = in.readNBytes(bytes, 0, 100);
            if (read < 100) throw new IOException(shp.getFileName() + " is shorter than a shapefile header.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt(0) != FILE_CODE) throw new IOException(shp.getFileName() + " is not a shapefile.");
        Header header = new Header();
        header.fileLengthBytes = buffer.getInt(24) * 2L;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        header.shapeType = buffer.getInt(32);
        header.minX = buffer.getDouble(36);
        header.minY = buffer.getDouble(44);
        header.maxX = buffer.getDouble(52);
        header.maxY = buffer.getDouble(60);
        return header;
    }

    /** Reads every polygon record; each record becomes a list of rings. */
    static List<List<Ring>> readPolygons(Path shp) throws IOException {
        byte[] all = Files.readAllBytes(shp);
        ByteBuffer buffer = ByteBuffer.wrap(all);
        List<List<Ring>> features = new ArrayList<>();
        int offset = 100;
        while (offset + 8 <= all.length) {
            buffer.order(ByteOrder.BIG_ENDIAN);
            int contentBytes = buffer.getInt(offset + 4) * 2;
            int content = offset + 8;
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int shapeType = buffer.getInt(content);
            List<Ring> rings = new ArrayList<>();
            if (shapeType == POLYGON) {
                int numParts = buffer.getInt(content + 36);
                int numPoints = buffer.getInt(content + 40);
                int[] parts = new int[numParts + 1];
                for (int p = 0; p < numParts; p++) {
                    parts[p] = buffer.getInt(content + 44 + p * 4);
                }
                parts[numParts] = numPoints;
                int pointsStart = content + 44 + numParts * 4;
                for (int p = 0; p < numParts; p++) {
                    int count = parts[p + 1] - parts[p];
                    if (count < 4) continue; // degenerate ring
                    double[] xs = new double[count];
                    double[] ys = new double[count];
                    for (int i = 0; i < count; i++) {
                        int at = pointsStart + (parts[p] + i) * 16;
                        xs[i] = buffer.getDouble(at);
                        ys[i] = buffer.getDouble(at + 8);
                    }
                    rings.add(new Ring(xs, ys));
                }
            } else if (shapeType != 0) {
                throw new IOException("Unsupported shape type " + shapeType + " (expected polygons).");
            }
            features.add(rings);
            offset = content + contentBytes;
        }
        return features;
    }

    /** Reads DBF field definitions. */
    static List<DbfField> readDbfFields(Path dbf) throws IOException {
        byte[] data = Files.readAllBytes(dbf);
        List<DbfField> fields = new ArrayList<>();
        int position = 32;
        while (position < data.length && data[position] != 0x0D) {
            String name = new String(data, position, 11, StandardCharsets.US_ASCII).split("\0")[0].trim();
            char type = (char) data[position + 11];
            int length = data[position + 16] & 0xff;
            fields.add(new DbfField(name, type, length));
            position += 32;
        }
        return fields;
    }

    /** Reads every DBF record as a map of field name to trimmed text value. */
    static List<Map<String, String>> readDbfRecords(Path dbf) throws IOException {
        byte[] data = Files.readAllBytes(dbf);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int recordCount = buffer.getInt(4);
        int headerLength = buffer.getShort(8) & 0xffff;
        int recordLength = buffer.getShort(10) & 0xffff;
        List<DbfField> fields = readDbfFields(dbf);
        List<Map<String, String>> records = new ArrayList<>();
        for (int r = 0; r < recordCount; r++) {
            int start = headerLength + r * recordLength + 1; // skip the deletion flag
            Map<String, String> values = new LinkedHashMap<>();
            int position = start;
            for (DbfField field : fields) {
                values.put(field.name, new String(data, position, field.length, StandardCharsets.US_ASCII).trim());
                position += field.length;
            }
            records.add(values);
        }
        return records;
    }
}
