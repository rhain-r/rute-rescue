package com.rutarescue.persistence;

import com.rutarescue.model.center.EvacuationCenter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the evacuation-center list bundled with the app (PLM SSC post, 24 Jul 2024), so residents
 * see centers every day, even before their LGU publishes any and even offline.
 * The rows use the CenterCsvReader format and stay unverified and unpublished. Each center gets a
 * fixed id from its line number ("plmssc-3") so the details screen can find it again.
 */
public final class BundledCenterReader {

    public static final String ASSET = "seed/centers_plmssc_2024_unverified.csv";
    /** Every bundled center id starts with this, which is how the app tells them apart. */
    public static final String ID_PREFIX = "plmssc-";

    private final CenterCsvReader csvReader = new CenterCsvReader();

    /** Returns the valid rows only; a bad row is skipped instead of hiding the whole list. */
    public List<EvacuationCenter> read(BufferedReader reader) throws IOException {
        List<EvacuationCenter> centers = new ArrayList<>();
        for (CenterCsvReader.Row row : csvReader.read(reader)) {
            if (!row.isValid()) continue;
            row.center.assignId(ID_PREFIX + row.lineNumber);
            centers.add(row.center);
        }
        return centers;
    }
}
