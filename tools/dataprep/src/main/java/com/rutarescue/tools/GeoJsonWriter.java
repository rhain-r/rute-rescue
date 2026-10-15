package com.rutarescue.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds simplified display polygons (outer ring + holes) for each hazard class and
 * writes them as a GeoJSON FeatureCollection with one MultiPolygon per class:
 *     { "type": "Feature", "properties": { "cls": 1 }, "geometry": { "type": "MultiPolygon", ... } }
 * Rings follow RFC 7946 orientation (outer counter-clockwise, holes clockwise).
 */
final class GeoJsonWriter {

    /** Statistics recorded in the manifest. */
    static final class Stats {
        int sourceRings;
        int outerRings;
        int holeRings;
        int polygonsWritten;
        int ringsWritten;
        long pointsWritten;
        int droppedTinyRings;
        int simplifiedWithSmallerTolerance;
        int keptOriginalGeometry;
        int holesAssignedByBoundingBox;
        int holesWithoutOuter;
    }

    /** One display polygon. */
    static final class Polygon {
        final List<double[][]> rings = new ArrayList<>();
        int decimals = 5;
    }

    private GeoJsonWriter() {
    }

    static List<Polygon> buildPolygons(List<Ring> rings, double tolerance, double minAreaDeg2, Stats stats) {
        stats.sourceRings += rings.size();
        // Group holes with their outer ring. ArcGIS writes each outer ring followed by its
        // holes, so the preceding outer ring is tried first; otherwise the smallest outer
        // ring whose bounding box contains the hole is used.
        List<Ring> outers = new ArrayList<>();
        List<List<Ring>> holesOf = new ArrayList<>();
        List<Ring> orphanHoles = new ArrayList<>();
        int lastOuter = -1;
        for (Ring ring : rings) {
            if (ring.isClockwise()) {
                outers.add(ring);
                holesOf.add(new ArrayList<>());
                lastOuter = outers.size() - 1;
                stats.outerRings++;
            } else {
                stats.holeRings++;
                if (lastOuter >= 0 && containsBox(outers.get(lastOuter), ring)) {
                    holesOf.get(lastOuter).add(ring);
                } else {
                    orphanHoles.add(ring);
                }
            }
        }
        for (Ring hole : orphanHoles) {
            int best = -1;
            double bestArea = Double.MAX_VALUE;
            for (int i = 0; i < outers.size(); i++) {
                Ring outer = outers.get(i);
                if (outer.absArea() < bestArea && containsBox(outer, hole)
                        && (outer.crossingsRightOf(hole.xs[0], hole.ys[0]) & 1) == 1) {
                    best = i;
                    bestArea = outer.absArea();
                }
            }
            if (best >= 0) {
                holesOf.get(best).add(hole);
                stats.holesAssignedByBoundingBox++;
            } else {
                stats.holesWithoutOuter++;
            }
        }

        List<Polygon> polygons = new ArrayList<>();
        for (int i = 0; i < outers.size(); i++) {
            Ring outer = outers.get(i);
            if (outer.absArea() < minAreaDeg2) {
                stats.droppedTinyRings += 1 + holesOf.get(i).size();
                continue;
            }
            List<Ring> holes = new ArrayList<>();
            for (Ring hole : holesOf.get(i)) {
                if (hole.absArea() >= minAreaDeg2) holes.add(hole); else stats.droppedTinyRings++;
            }
            Polygon polygon = simplifyPolygon(outer, holes, tolerance, stats);
            if (polygon != null) {
                polygons.add(polygon);
                stats.polygonsWritten++;
                stats.ringsWritten += polygon.rings.size();
                for (double[][] ring : polygon.rings) stats.pointsWritten += ring.length;
            }
        }
        return polygons;
    }

    private static Polygon simplifyPolygon(Ring outer, List<Ring> holes, double tolerance, Stats stats) {
        double tol = tolerance;
        for (int attempt = 0; attempt < 3; attempt++, tol /= 2) {
            Polygon polygon = new Polygon();
            double[][] outerPoints = Simplifier.simplifyRing(outer, tol);
            if (outerPoints == null) {
                if (attempt == 2) break;
                continue;
            }
            polygon.rings.add(outerPoints);
            for (Ring hole : holes) {
                double[][] holePoints = Simplifier.simplifyRing(hole, tol);
                if (holePoints != null) polygon.rings.add(holePoints);
            }
            if (Simplifier.isValid(polygon.rings)) {
                if (attempt > 0) stats.simplifiedWithSmallerTolerance++;
                return polygon;
            }
        }
        // Last resort: keep the original geometry untouched.
        Polygon original = new Polygon();
        original.decimals = 7;
        original.rings.add(Simplifier.original(outer));
        for (Ring hole : holes) original.rings.add(Simplifier.original(hole));
        stats.keptOriginalGeometry++;
        return original;
    }

    private static boolean containsBox(Ring outer, Ring inner) {
        return inner.minX >= outer.minX && inner.maxX <= outer.maxX
                && inner.minY >= outer.minY && inner.maxY <= outer.maxY;
    }

    static void write(Path file, Map<Integer, List<Polygon>> polygonsByClass, String scenarioName,
                      String datasetVersion) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("{\"type\":\"FeatureCollection\",\"name\":\"" + datasetVersion + "\",\"features\":[");
            boolean firstFeature = true;
            for (Map.Entry<Integer, List<Polygon>> entry : polygonsByClass.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                if (!firstFeature) out.write(',');
                firstFeature = false;
                out.write("\n{\"type\":\"Feature\",\"properties\":{\"cls\":" + entry.getKey()
                        + ",\"scenario\":\"" + scenarioName + "\"},\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":[");
                boolean firstPolygon = true;
                for (Polygon polygon : entry.getValue()) {
                    if (!firstPolygon) out.write(',');
                    firstPolygon = false;
                    writePolygon(out, polygon);
                }
                out.write("]}}");
            }
            out.write("\n]}\n");
        }
    }

    private static void writePolygon(Writer out, Polygon polygon) throws IOException {
        out.write('[');
        for (int r = 0; r < polygon.rings.size(); r++) {
            if (r > 0) out.write(',');
            double[][] ring = polygon.rings.get(r);
            // Shapefile outer rings are clockwise; RFC 7946 wants them counter-clockwise.
            boolean reverse = r == 0 ? signedArea(ring) < 0 : signedArea(ring) > 0;
            out.write('[');
            for (int i = 0; i < ring.length; i++) {
                double[] p = ring[reverse ? ring.length - 1 - i : i];
                if (i > 0) out.write(',');
                out.write('[');
                out.write(format(p[0], polygon.decimals));
                out.write(',');
                out.write(format(p[1], polygon.decimals));
                out.write(']');
            }
            out.write(']');
        }
        out.write(']');
    }

    private static double signedArea(double[][] ring) {
        double sum = 0;
        for (int i = 0; i < ring.length; i++) {
            double[] a = ring[i];
            double[] b = ring[(i + 1) % ring.length];
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum / 2;
    }

    /** Fixed-decimal formatting without String.format (much faster for millions of numbers). */
    static String format(double value, int decimals) {
        long scale = (long) Math.pow(10, decimals);
        long scaled = Math.round(value * scale);
        boolean negative = scaled < 0;
        scaled = Math.abs(scaled);
        long whole = scaled / scale;
        long fraction = scaled % scale;
        StringBuilder digits = new StringBuilder(Long.toString(fraction));
        while (digits.length() < decimals) digits.insert(0, '0');
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') end--;
        String fractionText = digits.substring(0, end);
        return (negative ? "-" : "") + whole + ("0".equals(fractionText) ? "" : "." + fractionText);
    }
}
