package com.rutarescue.model.hazard;

/**
 * The three prepared Project NOAH flood-hazard scenarios (DOCX section 7).
 *
 * A "5-year" scenario is a modelled flood with roughly a 20% chance of being equalled or
 * exceeded in any year. It is NOT a flood that happened five years ago, and it is never
 * live flooding.
 */
public enum FloodScenario {
    FIVE_YEAR(5, 20, "noah_5yr"),
    TWENTY_FIVE_YEAR(25, 4, "noah_25yr"),
    HUNDRED_YEAR(100, 1, "noah_100yr");

    /** The screen defaults to the more frequent 5-year scenario. */
    public static final FloodScenario DEFAULT = FIVE_YEAR;

    private final int returnPeriodYears;
    private final int annualChancePercent;
    private final String assetBaseName;

    FloodScenario(int returnPeriodYears, int annualChancePercent, String assetBaseName) {
        this.returnPeriodYears = returnPeriodYears;
        this.annualChancePercent = annualChancePercent;
        this.assetBaseName = assetBaseName;
    }

    public int getReturnPeriodYears() {
        return returnPeriodYears;
    }

    public int getAnnualChancePercent() {
        return annualChancePercent;
    }

    /** Short label for chips, e.g. "5-year". */
    public String getLabel() {
        return returnPeriodYears + "-year";
    }

    /** Explanation that is always visible next to the selected scenario. */
    public String getExplanation() {
        return "Prepared Project NOAH " + getLabel() + " scenario: a flood this large has about a "
                + annualChancePercent + "% chance in any year. It is a planning map, not live flooding.";
    }

    public String getGeoJsonAssetPath() {
        return "flood_data/" + assetBaseName + ".geojson";
    }

    public String getGridAssetPath() {
        return "flood_data/" + assetBaseName + ".grid";
    }

    public static FloodScenario fromYears(int years) {
        for (FloodScenario scenario : values()) {
            if (scenario.returnPeriodYears == years) return scenario;
        }
        throw new IllegalArgumentException("Unsupported return period: " + years);
    }

    public static FloodScenario fromName(String name) {
        if (name != null) {
            for (FloodScenario scenario : values()) {
                if (scenario.name().equalsIgnoreCase(name.strip())) return scenario;
            }
        }
        return DEFAULT;
    }
}
