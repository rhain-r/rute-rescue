package com.rutarescue.model.route;

/**
 * Display level for a route sample after the structure-aware rules run (DOCX section 8).
 * Declared from least to most severe, so ordinal() can be compared.
 */
public enum RiskLevel {
    GREEN("No mapped hazard"),
    AMBER("Caution"),
    RED("High risk"),
    UNAVAILABLE("Closed / unavailable");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWorseThan(RiskLevel other) {
        return ordinal() > other.ordinal();
    }

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
