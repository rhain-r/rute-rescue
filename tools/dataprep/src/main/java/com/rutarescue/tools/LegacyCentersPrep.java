package com.rutarescue.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ports the 90 evacuation centers hard-coded in the legacy Jabbacodeez app
 * (frontend/app/evacuation_centers.js) into a CSV seed file.
 *
 * IMPORTANT: these entries have only a name/address and coordinates, with no source or
 * verification date. They are therefore marked UNVERIFIED. LGU staff must review them on
 * the "Data & Centers" screen before they are published to residents.
 *
 * Usage: LegacyCentersPrep <evacuation_centers.js> <output csv>
 */
public final class LegacyCentersPrep {

    private static final Pattern CITY_COMMENT = Pattern.compile("^\\s*//\\s*([A-Z][A-Z ]+)\\s*$");
    private static final Pattern ADDRESS = Pattern.compile("address:\\s*\"([^\"]*)\"");
    private static final Pattern LAT = Pattern.compile("lat:\\s*(-?[0-9.]+)");
    private static final Pattern LON = Pattern.compile("lon:\\s*(-?[0-9.]+)");

    private static final Map<String, String> CITY_CODES = new LinkedHashMap<>();

    static {
        CITY_CODES.put("MANILA", "MANILA");
        CITY_CODES.put("CALOOCAN", "CALOOCAN");
        CITY_CODES.put("MALABON", "MALABON");
        CITY_CODES.put("NAVOTAS", "NAVOTAS");
        CITY_CODES.put("SAN JUAN", "SAN_JUAN");
        CITY_CODES.put("PASAY", "PASAY");
        CITY_CODES.put("MANDALUYONG", "MANDALUYONG");
        CITY_CODES.put("LAS PINAS", "LAS_PINAS");
        CITY_CODES.put("PARANAQUE", "PARANAQUE");
        CITY_CODES.put("QUEZON CITY", "QUEZON_CITY");
        CITY_CODES.put("MUNTINLUPA", "MUNTINLUPA");
        CITY_CODES.put("MARIKINA", "MARIKINA");
        CITY_CODES.put("VALENZUELA", "VALENZUELA");
        CITY_CODES.put("MAKATI", "MAKATI");
        CITY_CODES.put("TAGUIG", "TAGUIG");
        CITY_CODES.put("PASIG", "PASIG");
    }

    private LegacyCentersPrep() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: LegacyCentersPrep <evacuation_centers.js> <output csv>");
            System.exit(2);
        }
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        String source = "Legacy Jabbacodeez evacuation_centers.js (2025), UNVERIFIED";
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("name,address,city,latitude,longitude,source\n");
            String city = "";
            String address = null;
            String lat = null;
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher cityMatch = CITY_COMMENT.matcher(line);
                if (cityMatch.matches()) {
                    city = CITY_CODES.getOrDefault(cityMatch.group(1).trim(), "");
                    continue;
                }
                Matcher a = ADDRESS.matcher(line);
                if (a.find()) address = a.group(1).trim();
                Matcher la = LAT.matcher(line);
                if (la.find()) lat = la.group(1);
                Matcher lo = LON.matcher(line);
                if (lo.find() && address != null && lat != null) {
                    writer.write(csv(address) + "," + csv(address) + "," + city + "," + lat + "," + lo.group(1)
                            + "," + csv(source) + "\n");
                    count++;
                    address = null;
                    lat = null;
                }
            }
        }
        System.out.println("Wrote " + count + " legacy centers to " + output);
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
