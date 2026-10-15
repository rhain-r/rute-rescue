package com.rutarescue.model.center;

/**
 * The last headcount change, shown in the grey audit line of the capacity screen,
 * e.g. "+5 by Manila DRRM Demo at 9:42 AM · source: barangay field check". Immutable.
 */
public final class CapacityChange {

    private final int fromCount;
    private final int toCount;
    private final String actorName;
    private final long changedAt;
    private final String source;

    public CapacityChange(int fromCount, int toCount, String actorName, long changedAt, String source) {
        this.fromCount = fromCount;
        this.toCount = toCount;
        this.actorName = actorName == null ? "" : actorName.strip();
        this.changedAt = changedAt;
        this.source = source == null ? "" : source.strip();
    }

    public int getDelta() {
        return toCount - fromCount;
    }

    /** "+5 by Manila DRRM Demo" (the time and source are added by the screen). */
    public String describe() {
        int delta = getDelta();
        String amount = delta > 0 ? "+" + delta : delta == 0 ? "No change" : String.valueOf(delta);
        return actorName.isEmpty() ? amount : amount + " by " + actorName;
    }

    public int getFromCount() { return fromCount; }
    public int getToCount() { return toCount; }
    public String getActorName() { return actorName; }
    public long getChangedAt() { return changedAt; }
    public String getSource() { return source; }
}
