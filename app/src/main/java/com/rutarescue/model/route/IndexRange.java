package com.rutarescue.model.route;

/**
 * An inclusive range of polyline vertex indices, used for provider route sections such
 * as "this part is a tunnel" or "traffic reports a road closure here".
 */
public final class IndexRange {

    private final int start;
    private final int end;

    public IndexRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid index range " + start + ".." + end);
        }
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    /** A vertex index belongs to the range if start <= index < end (the end vertex starts the next part). */
    public boolean containsSegmentStartingAt(int vertexIndex) {
        return vertexIndex >= start && vertexIndex < Math.max(end, start + 1);
    }
}
