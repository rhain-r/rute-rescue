package com.rutarescue.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display-only simplification of polygon rings: Douglas-Peucker, coordinates rounded to
 * 5 decimals (about 1 m), then a validity check. If a simplified polygon would cross
 * itself, a smaller tolerance is tried; as a last resort the original ring is kept, so
 * simplification never CREATES an invalid polygon (DOCX section 7 gate).
 * The hazard grid used for route assessment is built from the ORIGINAL rings.
 */
final class Simplifier {

    static final double ROUND = 1e5;

    private Simplifier() {
    }

    /** Douglas-Peucker on a closed ring; returns null when the ring collapses. */
    static double[][] simplifyRing(Ring ring, double tolerance) {
        int n = ring.size();
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        // Split the closed ring at the point farthest from the start.
        int far = 0;
        double best = -1;
        for (int i = 1; i < n - 1; i++) {
            double dx = ring.xs[i] - ring.xs[0];
            double dy = ring.ys[i] - ring.ys[0];
            double d = dx * dx + dy * dy;
            if (d > best) {
                best = d;
                far = i;
            }
        }
        keep[far] = true;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, far});
        stack.push(new int[]{far, n - 1});
        double tol2 = tolerance * tolerance;
        while (!stack.isEmpty()) {
            int[] span = stack.pop();
            int a = span[0], b = span[1];
            if (b - a < 2) continue;
            int index = -1;
            double maxDistance = -1;
            for (int i = a + 1; i < b; i++) {
                double d = segmentDistanceSquared(ring.xs[i], ring.ys[i], ring.xs[a], ring.ys[a], ring.xs[b], ring.ys[b]);
                if (d > maxDistance) {
                    maxDistance = d;
                    index = i;
                }
            }
            if (maxDistance > tol2) {
                keep[index] = true;
                stack.push(new int[]{a, index});
                stack.push(new int[]{index, b});
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!keep[i]) continue;
            double x = Math.round(ring.xs[i] * ROUND) / ROUND;
            double y = Math.round(ring.ys[i] * ROUND) / ROUND;
            if (!out.isEmpty()) {
                double[] last = out.get(out.size() - 1);
                if (last[0] == x && last[1] == y) continue;
            }
            out.add(new double[]{x, y});
        }
        if (out.size() < 4) return null;
        double[] first = out.get(0);
        double[] last = out.get(out.size() - 1);
        if (first[0] != last[0] || first[1] != last[1]) out.add(new double[]{first[0], first[1]});
        if (out.size() < 4 || Math.abs(area(out)) == 0) return null;
        return out.toArray(new double[0][]);
    }

    static double[][] original(Ring ring) {
        double[][] out = new double[ring.size()][];
        for (int i = 0; i < ring.size(); i++) out[i] = new double[]{ring.xs[i], ring.ys[i]};
        return out;
    }

    static double area(List<double[]> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            double[] a = points.get(i);
            double[] b = points.get((i + 1) % points.size());
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum / 2;
    }

    private static double segmentDistanceSquared(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx - px, cy = ay + t * dy - py;
        return cx * cx + cy * cy;
    }

    /**
     * True when no two non-adjacent edges of the polygon (outer ring + holes) properly
     * cross or overlap. Edges touching only at a shared vertex are allowed.
     */
    static boolean isValid(List<double[][]> polygonRings) {
        List<double[]> segments = new ArrayList<>(); // x1, y1, x2, y2, ringIndex, edgeIndex, ringEdgeCount
        for (int r = 0; r < polygonRings.size(); r++) {
            double[][] ring = polygonRings.get(r);
            int edges = ring.length - 1;
            for (int i = 0; i < edges; i++) {
                segments.add(new double[]{ring[i][0], ring[i][1], ring[i + 1][0], ring[i + 1][1], r, i, edges});
            }
        }
        double cell = 0.0005;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int s = 0; s < segments.size(); s++) {
            double[] g = segments.get(s);
            int x0 = (int) Math.floor(Math.min(g[0], g[2]) / cell), x1 = (int) Math.floor(Math.max(g[0], g[2]) / cell);
            int y0 = (int) Math.floor(Math.min(g[1], g[3]) / cell), y1 = (int) Math.floor(Math.max(g[1], g[3]) / cell);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    grid.computeIfAbsent(((long) x << 32) ^ (y & 0xffffffffL), k -> new ArrayList<>()).add(s);
                }
            }
        }
        for (List<Integer> bucket : grid.values()) {
            for (int a = 0; a < bucket.size(); a++) {
                for (int b = a + 1; b < bucket.size(); b++) {
                    double[] s1 = segments.get(bucket.get(a));
                    double[] s2 = segments.get(bucket.get(b));
                    if (adjacent(s1, s2)) continue;
                    if (properlyIntersect(s1, s2)) return false;
                }
            }
        }
        return true;
    }

    private static boolean adjacent(double[] s1, double[] s2) {
        if (s1[4] != s2[4]) return false;
        int i = (int) s1[5], j = (int) s2[5], edges = (int) s1[6];
        int diff = Math.abs(i - j);
        return diff <= 1 || diff == edges - 1;
    }

    private static boolean properlyIntersect(double[] s1, double[] s2) {
        double d1 = cross(s2[0], s2[1], s2[2], s2[3], s1[0], s1[1]);
        double d2 = cross(s2[0], s2[1], s2[2], s2[3], s1[2], s1[3]);
        double d3 = cross(s1[0], s1[1], s1[2], s1[3], s2[0], s2[1]);
        double d4 = cross(s1[0], s1[1], s1[2], s1[3], s2[2], s2[3]);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true; // the segments cross at an interior point
        }
        // Collinear overlap (not just touching at an endpoint) also makes a polygon invalid.
        if (d1 == 0 && d2 == 0 && d3 == 0 && d4 == 0) {
            return overlapLength(s1, s2) > 0;
        }
        return false;
    }

    private static double overlapLength(double[] s1, double[] s2) {
        boolean useX = Math.abs(s1[2] - s1[0]) >= Math.abs(s1[3] - s1[1]);
        double a0 = useX ? Math.min(s1[0], s1[2]) : Math.min(s1[1], s1[3]);
        double a1 = useX ? Math.max(s1[0], s1[2]) : Math.max(s1[1], s1[3]);
        double b0 = useX ? Math.min(s2[0], s2[2]) : Math.min(s2[1], s2[3]);
        double b1 = useX ? Math.max(s2[0], s2[2]) : Math.max(s2[1], s2[3]);
        return Math.min(a1, b1) - Math.max(a0, b0);
    }

    private static double cross(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
