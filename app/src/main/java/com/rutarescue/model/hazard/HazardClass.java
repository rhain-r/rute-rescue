package com.rutarescue.model.hazard;

/**
 * Flood-hazard class using the official NOAH legend (DOCX section 7).
 *
 *   Var 1 = LOW    0-0.5 m      yellow
 *   Var 2 = MEDIUM >0.5-1.5 m   orange
 *   Var 3 = HIGH   >1.5 m       red
 *
 * NONE means "no mapped hazard in this scenario". It does NOT mean "safe".
 */
public enum HazardClass {
    NONE(0, "No mapped hazard", "outside mapped flood areas", "#00000000"),
    LOW(1, "Low", "0-0.5 m", "#F5C842"),
    MEDIUM(2, "Medium", "0.5-1.5 m", "#F08C2E"),
    HIGH(3, "High", "over 1.5 m", "#E0483E");

    private final int code;
    private final String label;
    private final String depthRange;
    private final String colorHex;

    HazardClass(int code, String label, String depthRange, String colorHex) {
        this.code = code;
        this.label = label;
        this.depthRange = depthRange;
        this.colorHex = colorHex;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }
    public String getDepthRange() { return depthRange; }
    public String getColorHex() { return colorHex; }

    public boolean isMoreSevereThan(HazardClass other) {
        return code > other.code;
    }

    public static HazardClass max(HazardClass a, HazardClass b) {
        return a.code >= b.code ? a : b;
    }

    public static HazardClass fromCode(int code) {
        switch (code) {
            case 0: return NONE;
            case 1: return LOW;
            case 2: return MEDIUM;
            case 3: return HIGH;
            default: throw new IllegalArgumentException("Unknown hazard class code: " + code);
        }
    }

    /**
     * Normalizes a NOAH "Var" attribute. The 5- and 25-year DBF files store it as a float
     * ("1.00000000000e+000") and the 100-year file as a numeric ("1.000"); both become an
     * integer enum here. Anything other than 1, 2 or 3 is rejected.
     */
    public static HazardClass fromVar(double var) {
        long rounded = Math.round(var);
        if (Math.abs(var - rounded) > 1e-6 || rounded < 1 || rounded > 3) {
            throw new IllegalArgumentException("NOAH Var must be 1, 2 or 3 but was " + var);
        }
        return fromCode((int) rounded);
    }

    public static HazardClass fromVarText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("NOAH Var is empty.");
        }
        return fromVar(Double.parseDouble(text.strip()));
    }
}
