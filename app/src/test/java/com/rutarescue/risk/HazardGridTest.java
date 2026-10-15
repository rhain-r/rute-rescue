package com.rutarescue.risk;

import com.rutarescue.exception.MalformedDataException;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HazardGridTest {

    /** 3 x 2 grid, row 0 (south) = NONE LOW MEDIUM, row 1 (north) = HIGH NONE LOW. */
    private static HazardGrid tiny() {
        return HazardGrid.fromCodes(FloodScenario.TWENTY_FIVE_YEAR, 121.0, 14.5, 0.01, 3, 2,
                new int[]{0, 1, 2, 3, 0, 1});
    }

    @Test
    public void classifiesByCell() {
        HazardGrid grid = tiny();
        assertEquals(HazardClass.NONE, grid.classify(14.505, 121.005));
        assertEquals(HazardClass.MEDIUM, grid.classify(14.505, 121.025));
        assertEquals(HazardClass.HIGH, grid.classify(14.515, 121.005));
        assertTrue(grid.covers(14.515, 121.025));
        assertFalse(grid.covers(14.6, 121.0));
        assertEquals(HazardClass.NONE, grid.classify(14.6, 121.0));
    }

    @Test
    public void binaryRoundTrip() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tiny().write(bytes);
        HazardGrid read = HazardGrid.read(new ByteArrayInputStream(bytes.toByteArray()),
                FloodScenario.TWENTY_FIVE_YEAR, "v-test");
        assertEquals(HazardClass.LOW, read.classify(14.515, 121.025));
        assertEquals("v-test", read.getDatasetVersion());
    }

    @Test
    public void wrongScenarioIsRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tiny().write(bytes);
        assertThrows(MalformedDataException.class, () -> HazardGrid.read(
                new ByteArrayInputStream(bytes.toByteArray()), FloodScenario.HUNDRED_YEAR, "v"));
    }

    @Test
    public void truncatedFileIsRejected() throws Exception {
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        HazardGrid.fromCodes(FloodScenario.FIVE_YEAR, 121.0, 14.5, 0.001, 100, 100, new int[10_000]).write(full);
        // Re-compress only the first part of the uncompressed data to simulate a cut-off file.
        ByteArrayOutputStream cut = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(cut)) {
            byte[] raw = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(full.toByteArray())).readAllBytes();
            gzip.write(Arrays.copyOf(raw, raw.length / 2));
        }
        assertThrows(MalformedDataException.class, () -> HazardGrid.read(
                new ByteArrayInputStream(cut.toByteArray()), FloodScenario.FIVE_YEAR, "v"));
    }

    @Test
    public void noahVarValuesAreNormalized() {
        assertEquals(HazardClass.LOW, HazardClass.fromVarText("1.00000000000e+000"));
        assertEquals(HazardClass.MEDIUM, HazardClass.fromVarText("2.000"));
        assertEquals(HazardClass.HIGH, HazardClass.fromVar(3.0));
        assertThrows(IllegalArgumentException.class, () -> HazardClass.fromVar(4));
        assertThrows(IllegalArgumentException.class, () -> HazardClass.fromVar(1.5));
    }
}
