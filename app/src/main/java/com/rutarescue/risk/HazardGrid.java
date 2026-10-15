package com.rutarescue.risk;

import com.rutarescue.exception.MalformedDataException;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A compact, fixed-resolution hazard grid for one NOAH scenario (DOCX section 7).
 *
 * The raw shapefiles have millions of points, which is far too slow to query on a phone.
 * The build tool (tools/dataprep) rasterizes them once into cells of about 11 m, storing
 * the class of each cell in 2 bits (4 cells per byte). Looking up a point is then just an
 * array index calculation.
 *
 * File format (binary, GZIP-compressed, written with DataOutputStream):
 *   int    magic "RRHG"
 *   int    format version (1)
 *   int    return period in years (5, 25 or 100)
 *   double minimum longitude, double minimum latitude, double cell size in degrees
 *   int    columns, int rows
 *   int    packed byte count, then the packed bytes (row 0 = southern edge)
 */
public final class HazardGrid implements HazardLookup {

    public static final int MAGIC = 0x52524847; // "RRHG"
    public static final int FORMAT_VERSION = 1;

    private final FloodScenario scenario;
    private final String datasetVersion;
    private final double minLon;
    private final double minLat;
    private final double cellSizeDeg;
    private final int cols;
    private final int rows;
    private final byte[] packed;

    public HazardGrid(FloodScenario scenario, String datasetVersion, double minLon, double minLat,
                      double cellSizeDeg, int cols, int rows, byte[] packed) {
        if (scenario == null) throw new IllegalArgumentException("Scenario is required.");
        if (cellSizeDeg <= 0 || cols <= 0 || rows <= 0) throw new IllegalArgumentException("Invalid grid size.");
        long cells = (long) cols * rows;
        if (packed == null || packed.length != (int) ((cells + 3) / 4)) {
            throw new IllegalArgumentException("Packed data does not match " + cols + " x " + rows + " cells.");
        }
        this.scenario = scenario;
        this.datasetVersion = datasetVersion == null ? "" : datasetVersion;
        this.minLon = minLon;
        this.minLat = minLat;
        this.cellSizeDeg = cellSizeDeg;
        this.cols = cols;
        this.rows = rows;
        this.packed = packed;
    }

    @Override
    public FloodScenario getScenario() {
        return scenario;
    }

    @Override
    public String getDatasetVersion() {
        return datasetVersion;
    }

    @Override
    public boolean covers(double latitude, double longitude) {
        return longitude >= minLon && longitude < minLon + cols * cellSizeDeg
                && latitude >= minLat && latitude < minLat + rows * cellSizeDeg;
    }

    @Override
    public HazardClass classify(double latitude, double longitude) {
        if (!covers(latitude, longitude)) {
            return HazardClass.NONE;
        }
        int col = (int) Math.floor((longitude - minLon) / cellSizeDeg);
        int row = (int) Math.floor((latitude - minLat) / cellSizeDeg);
        return HazardClass.fromCode(codeAt(row, col));
    }

    private int codeAt(int row, int col) {
        long index = (long) row * cols + col;
        int shift = (int) (index & 3) * 2;
        return (packed[(int) (index >>> 2)] >> shift) & 3;
    }

    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public double getCellSizeDeg() { return cellSizeDeg; }
    public double getMinLon() { return minLon; }
    public double getMinLat() { return minLat; }

    /**
     * Reads a grid written by the data preparation tool.
     * Lab concepts: binary streams (DataInputStream), stream chaining (GZIP + buffer),
     * try-with-resources, and a specific exception for truncated or foreign files.
     */
    public static HazardGrid read(InputStream rawStream, FloodScenario expected, String datasetVersion)
            throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(rawStream)))) {
            if (in.readInt() != MAGIC) {
                throw new MalformedDataException("Not a RutaRescue hazard grid file.");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new MalformedDataException("Unsupported hazard grid version " + version + ".");
            }
            int years = in.readInt();
            if (years != expected.getReturnPeriodYears()) {
                throw new MalformedDataException("Grid is for the " + years + "-year scenario, expected "
                        + expected.getLabel() + ".");
            }
            double minLon = in.readDouble();
            double minLat = in.readDouble();
            double cell = in.readDouble();
            int cols = in.readInt();
            int rows = in.readInt();
            int byteCount = in.readInt();
            if (cols <= 0 || rows <= 0 || byteCount != (int) (((long) cols * rows + 3) / 4)) {
                throw new MalformedDataException("Hazard grid header is inconsistent.");
            }
            byte[] packed = new byte[byteCount];
            in.readFully(packed);
            return new HazardGrid(expected, datasetVersion, minLon, minLat, cell, cols, rows, packed);
        } catch (EOFException truncated) {
            throw new MalformedDataException("Hazard grid file is truncated.", truncated);
        }
    }

    /** Writes this grid in the same format (used by tests; the build tool has its own writer). */
    public void write(OutputStream rawStream) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(rawStream)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(scenario.getReturnPeriodYears());
            out.writeDouble(minLon);
            out.writeDouble(minLat);
            out.writeDouble(cellSizeDeg);
            out.writeInt(cols);
            out.writeInt(rows);
            out.writeInt(packed.length);
            out.write(packed);
        }
    }

    /** Builds a grid from one class code per cell (row-major, row 0 = south). Handy for tests. */
    public static HazardGrid fromCodes(FloodScenario scenario, double minLon, double minLat, double cellSizeDeg,
                                       int cols, int rows, int[] codes) {
        if (codes.length != cols * rows) throw new IllegalArgumentException("Expected " + cols * rows + " codes.");
        byte[] packed = new byte[(cols * rows + 3) / 4];
        for (int i = 0; i < codes.length; i++) {
            int code = codes[i] & 3;
            packed[i >>> 2] |= (byte) (code << ((i & 3) * 2));
        }
        return new HazardGrid(scenario, "test", minLon, minLat, cellSizeDeg, cols, rows, packed);
    }
}
