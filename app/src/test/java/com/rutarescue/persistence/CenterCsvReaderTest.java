package com.rutarescue.persistence;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Center lists: the original 6-column file and the PLM SSC list with location details. */
public class CenterCsvReaderTest {

    private static List<CenterCsvReader.Row> read(String text) throws IOException {
        return new CenterCsvReader().read(new BufferedReader(new StringReader(text)));
    }

    @Test
    public void stillReadsTheSixColumnFile() throws IOException {
        List<CenterCsvReader.Row> rows = read(CenterCsvReader.HEADER + "\n"
                + "\"Building 3, Barangay 128\",\"Building 3, Barangay 128\",MANILA,14.631,120.962,legacy\n");
        assertTrue(rows.get(0).isValid());
        assertEquals("", rows.get(0).center.getLandmark());
    }

    @Test
    public void readsBarangayLandmarkAndLocationFlag() throws IOException {
        List<CenterCsvReader.Row> rows = read(CenterCsvReader.EXTENDED_HEADER + "\n"
                + "Barangay 105 Covered Court,Barangay 105 Covered Court,MANILA,14.6152,120.9698,"
                + "PLM SSC 2024,105,Near Barangay 105 hall,false\n");
        CenterCsvReader.Row row = rows.get(0);
        assertTrue(row.isValid());
        assertEquals("105", row.center.getBarangay());
        assertEquals("Near Barangay 105 hall", row.center.getLandmark());
        assertFalse(row.center.isLocationVerified());
    }

    @Test
    public void extendedRowsNeedNineColumns() throws IOException {
        List<CenterCsvReader.Row> rows = read(CenterCsvReader.EXTENDED_HEADER + "\n"
                + "Court,Court,MANILA,14.6,120.97,src\n");
        assertFalse(rows.get(0).isValid());
        assertTrue(rows.get(0).error.contains("9"));
    }
}
