package com.rutarescue.persistence;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.center.AccessibilityFeature;
import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.center.VerificationInfo;
import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.common.MetroManilaCity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Reads an evacuation-center CSV for the LGU "import, validate, preview, publish" flow
 * (DOCX section 3). Expected header:
 *     name,address,city,latitude,longitude,source
 * Every row is validated with the EvacuationCenter constructor; bad rows are reported
 * with their line number instead of stopping the import.
 */
public final class CenterCsvReader {

    public static final String HEADER = "name,address,city,latitude,longitude,source";

    /** One previewed row: either a valid (still unverified) center or an error. */
    public static final class Row {
        public final int lineNumber;
        public final EvacuationCenter center;
        public final String source;
        public final String error;

        Row(int lineNumber, EvacuationCenter center, String source, String error) {
            this.lineNumber = lineNumber;
            this.center = center;
            this.source = source;
            this.error = error;
        }

        public boolean isValid() {
            return center != null;
        }
    }

    public List<Row> read(BufferedReader reader) throws IOException {
        List<Row> rows = new ArrayList<>();
        String header = reader.readLine();
        if (header == null) {
            rows.add(new Row(1, null, "", "The file is empty."));
            return rows;
        }
        if (header.startsWith("\uFEFF")) header = header.substring(1);
        if (!HEADER.equals(header.strip().toLowerCase(Locale.ROOT))) {
            rows.add(new Row(1, null, "", "Unexpected header. Use: " + HEADER));
            return rows;
        }
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            rows.add(parseRow(lineNumber, line));
        }
        return rows;
    }

    private static Row parseRow(int lineNumber, String line) {
        List<String> fields = splitCsv(line);
        if (fields.size() != 6) {
            return new Row(lineNumber, null, "", "Expected 6 columns but found " + fields.size() + ".");
        }
        MetroManilaCity city = MetroManilaCity.fromLooseName(fields.get(2));
        if (city == null) {
            return new Row(lineNumber, null, fields.get(5), "Unknown city \"" + fields.get(2) + "\".");
        }
        try {
            double lat = Double.parseDouble(fields.get(3).strip());
            double lon = Double.parseDouble(fields.get(4).strip());
            EvacuationCenter center = new EvacuationCenter(null, fields.get(0), fields.get(1), city,
                    new GeoPoint(lat, lon), CenterStatus.NOT_REPORTED, EnumSet.noneOf(AccessibilityFeature.class),
                    VerificationInfo.unverified(fields.get(5)), false, "");
            return new Row(lineNumber, center, fields.get(5), null);
        } catch (NumberFormatException badNumber) {
            return new Row(lineNumber, null, fields.get(5), "Latitude/longitude must be decimal numbers.");
        } catch (ValidationException | IllegalArgumentException invalid) {
            return new Row(lineNumber, null, fields.get(5), invalid.getMessage());
        }
    }

    /** Splits one CSV line, honouring double-quoted fields with commas and "" escapes. */
    static List<String> splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
