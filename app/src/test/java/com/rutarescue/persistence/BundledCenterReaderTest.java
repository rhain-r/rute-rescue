package com.rutarescue.persistence;

import com.rutarescue.model.center.CenterStatus;
import com.rutarescue.model.center.EvacuationCenter;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The PLM SSC list bundled with the app, shown to residents every day. */
public class BundledCenterReaderTest {

    @Test
    public void readsTheBundledPlmSscListWithStableIds() throws IOException {
        File file = new File("src/main/assets/" + BundledCenterReader.ASSET);
        if (!file.exists()) file = new File("app/src/main/assets/" + BundledCenterReader.ASSET);
        List<EvacuationCenter> centers;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            centers = new BundledCenterReader().read(reader);
        }
        // 91 matched pins, minus "Vista Mall" (labelled Manila, pinned in Taguig) and "San Roque de
        // Navotas" (pinned in San Juan), removed on 2026-10-19 until their real locations are confirmed.
        assertEquals(89, centers.size());
        Set<String> ids = new HashSet<>();
        for (EvacuationCenter center : centers) {
            assertTrue(center.getId().startsWith(BundledCenterReader.ID_PREFIX));
            assertFalse(center.getVerification().isVerified());
            assertFalse(center.isPublished());
            assertEquals(CenterStatus.NOT_REPORTED, center.getStatus());
            ids.add(center.getId());
        }
        assertEquals("every center has its own id", centers.size(), ids.size());
    }

    @Test
    public void skipsBadRowsAndKeepsTheLineNumberInTheId() throws IOException {
        String text = CenterCsvReader.HEADER + "\n"
                + "Not a real row\n"
                + "\"Building 3, Barangay 128\",\"Building 3, Barangay 128\",MANILA,14.631,120.962,PLM SSC\n";
        List<EvacuationCenter> centers = new BundledCenterReader().read(new BufferedReader(new StringReader(text)));
        assertEquals(1, centers.size());
        assertEquals(BundledCenterReader.ID_PREFIX + "3", centers.get(0).getId());
    }
}
