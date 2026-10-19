package com.rutarescue.model.common;

import java.util.Locale;

/**
 * The 17 local government units of Metro Manila. Used as the "jurisdiction" of residents,
 * LGU staff, centers, advisories and Ligtas applications.
 */
public enum MetroManilaCity {
    // City (or municipal) hall of each LGU, from OpenStreetMap (Nominatim, checked 2026-10-19).
    // Used as the resident's reference point when the phone's location is not known yet.
    CALOOCAN("Caloocan", 14.64885, 120.99061),
    LAS_PINAS("Las Piñas", 14.44970, 120.98260),
    MAKATI("Makati", 14.57049, 121.02722),
    MALABON("Malabon", 14.65771, 120.95094),
    MANDALUYONG("Mandaluyong", 14.57770, 121.03366),
    MANILA("Manila", 14.58930, 120.98164),
    MARIKINA("Marikina", 14.63303, 121.09895),
    MUNTINLUPA("Muntinlupa", 14.39511, 121.04419),
    NAVOTAS("Navotas", 14.65819, 120.94781),
    PARANAQUE("Parañaque", 14.47054, 121.02231),
    PASAY("Pasay", 14.54394, 120.99537),
    PASIG("Pasig", 14.55982, 121.08157),
    PATEROS("Pateros", 14.54212, 121.06459),
    QUEZON_CITY("Quezon City", 14.64638, 121.05001),
    SAN_JUAN("San Juan", 14.60475, 121.02937),
    TAGUIG("Taguig", 14.52894, 121.06993),
    VALENZUELA("Valenzuela", 14.69274, 120.96815);

    private final String displayName;
    private final double hallLatitude;
    private final double hallLongitude;

    MetroManilaCity(String displayName, double hallLatitude, double hallLongitude) {
        this.displayName = displayName;
        this.hallLatitude = hallLatitude;
        this.hallLongitude = hallLongitude;
    }

    /** Where the city (or municipal) hall is: the city's reference point when GPS is not available. */
    public GeoPoint getHallLocation() {
        return new GeoPoint(hallLatitude, hallLongitude);
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The value stored in Firestore (the enum name, e.g. "QUEZON_CITY"). */
    public String code() {
        return name();
    }

    /** Converts a stored code back to the enum; returns null for unknown codes. */
    public static MetroManilaCity fromCode(String code) {
        if (code == null) return null;
        for (MetroManilaCity city : values()) {
            if (city.name().equalsIgnoreCase(code.strip())) return city;
        }
        return null;
    }

    /** Matches either a code or a display name, e.g. "Las Pinas" or "QUEZON CITY". */
    public static MetroManilaCity fromLooseName(String text) {
        if (text == null) return null;
        String wanted = normalize(text);
        for (MetroManilaCity city : values()) {
            if (normalize(city.name()).equals(wanted) || normalize(city.displayName).equals(wanted)) {
                return city;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.strip().toUpperCase(Locale.ROOT)
                .replace('Ñ', 'N').replace('_', ' ').replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
