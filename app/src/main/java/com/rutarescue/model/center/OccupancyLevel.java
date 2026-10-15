package com.rutarescue.model.center;

/** Colour band of the capacity gauge: green under 70%, amber from 70% to 90%, red above 90%. */
public enum OccupancyLevel {
    NOT_SET,
    GREEN,
    AMBER,
    RED;

    public static final double AMBER_FROM = 0.70;
    public static final double RED_ABOVE = 0.90;

    /** @param fraction headcount divided by capacity (1.0 = exactly full). */
    public static OccupancyLevel of(double fraction) {
        if (fraction < AMBER_FROM) return GREEN;
        if (fraction <= RED_ABOVE) return AMBER;
        return RED;
    }
}
