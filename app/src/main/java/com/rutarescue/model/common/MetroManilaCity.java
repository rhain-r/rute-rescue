package com.rutarescue.model.common;

import java.util.Locale;

/**
 * The 17 local government units of Metro Manila. Used as the "jurisdiction" of residents,
 * LGU staff, centers, advisories and Ligtas applications.
 */
public enum MetroManilaCity {
    CALOOCAN("Caloocan"),
    LAS_PINAS("Las Piñas"),
    MAKATI("Makati"),
    MALABON("Malabon"),
    MANDALUYONG("Mandaluyong"),
    MANILA("Manila"),
    MARIKINA("Marikina"),
    MUNTINLUPA("Muntinlupa"),
    NAVOTAS("Navotas"),
    PARANAQUE("Parañaque"),
    PASAY("Pasay"),
    PASIG("Pasig"),
    PATEROS("Pateros"),
    QUEZON_CITY("Quezon City"),
    SAN_JUAN("San Juan"),
    TAGUIG("Taguig"),
    VALENZUELA("Valenzuela");

    private final String displayName;

    MetroManilaCity(String displayName) {
        this.displayName = displayName;
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
