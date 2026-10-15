package com.rutarescue.model.center;

import com.rutarescue.exception.ValidationException;

/**
 * An evacuation center's capacity and the headcount LGU staff last entered.
 *
 * Encapsulation: the object is immutable and always valid. A change creates a new object
 * through withOccupancy() or adjustedBy(), which apply the same checks as the constructor.
 * The headcount may go up to 120% of capacity (overflow space); anything above is refused.
 */
public final class CenterCapacity {

    public static final double LIMIT_RATIO = 1.20;

    private final int totalPersons;
    private final int currentOccupancy;

    public CenterCapacity(int totalPersons, int currentOccupancy) throws ValidationException {
        if (totalPersons < 0) {
            throw new ValidationException("capacity", "Capacity cannot be negative.");
        }
        if (currentOccupancy < 0) {
            throw new ValidationException("occupancy", "The headcount cannot be negative.");
        }
        if (totalPersons == 0 && currentOccupancy > 0) {
            throw new ValidationException("occupancy", "Set the center's capacity before entering a headcount.");
        }
        int limit = limitFor(totalPersons);
        if (currentOccupancy > limit) {
            throw new ValidationException("occupancy", currentOccupancy + " is above the limit of " + limit
                    + " people (120% of capacity).");
        }
        this.totalPersons = totalPersons;
        this.currentOccupancy = currentOccupancy;
    }

    /** A center whose capacity has not been entered yet. */
    public static CenterCapacity notSet() {
        try {
            return new CenterCapacity(0, 0);
        } catch (ValidationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int limitFor(int totalPersons) {
        return (int) Math.floor(totalPersons * LIMIT_RATIO + 1e-9);
    }

    /** Same capacity with an exact headcount (the "Set" field). */
    public CenterCapacity withOccupancy(int newCount) throws ValidationException {
        return new CenterCapacity(totalPersons, newCount);
    }

    /** Same capacity with the headcount changed by a quick button; never goes below zero. */
    public CenterCapacity adjustedBy(int delta) throws ValidationException {
        return new CenterCapacity(totalPersons, Math.max(0, currentOccupancy + delta));
    }

    /** Same headcount with a new capacity (checked against the new limit). */
    public CenterCapacity withTotal(int newTotal) throws ValidationException {
        return new CenterCapacity(newTotal, currentOccupancy);
    }

    public boolean isSet() {
        return totalPersons > 0;
    }

    public int getLimit() {
        return limitFor(totalPersons);
    }

    /** Headcount divided by capacity; 0 when the capacity is not set. */
    public double getFraction() {
        return totalPersons == 0 ? 0 : (double) currentOccupancy / totalPersons;
    }

    public int getPercent() {
        return (int) Math.round(getFraction() * 100);
    }

    public OccupancyLevel getLevel() {
        return isSet() ? OccupancyLevel.of(getFraction()) : OccupancyLevel.NOT_SET;
    }

    /** Status suggested by the headcount: Near capacity from 70%, Full at 100%. */
    public CenterStatus suggestedStatus() {
        if (!isSet()) return CenterStatus.NOT_REPORTED;
        double fraction = getFraction();
        if (fraction >= 1.0) return CenterStatus.FULL;
        if (fraction >= OccupancyLevel.AMBER_FROM) return CenterStatus.NEAR_CAPACITY;
        return CenterStatus.OPEN;
    }

    /** For residents: "113 of 120 people · 94%", or "Headcount not posted yet". */
    public String summary() {
        if (!isSet()) return "Headcount not posted yet";
        return currentOccupancy + " of " + totalPersons + " people · " + getPercent() + "%";
    }

    /** For center rows: "113/120 people", or empty when nothing was posted. */
    public String shortSummary() {
        return isSet() ? currentOccupancy + "/" + totalPersons + " people" : "";
    }

    public int getTotalPersons() { return totalPersons; }
    public int getCurrentOccupancy() { return currentOccupancy; }
}
