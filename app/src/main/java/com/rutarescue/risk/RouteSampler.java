package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.util.GeoMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cuts a route polyline into equal pieces (default 10 m) and returns the middle point of
 * each piece. Every sample "represents" its piece's length, so exposure is measured in
 * metres no matter how many vertices the provider used. (The legacy code used segment
 * counts and midpoints, which over-counted short segments.)
 */
public final class RouteSampler {

    /** One sampled point along the route. */
    public static final class SamplePoint {
        private final GeoPoint point;
        private final double distanceAlongMeters;
        private final double lengthMeters;
        private final double bearingDegrees;
        private final int vertexIndex;

        SamplePoint(GeoPoint point, double distanceAlongMeters, double lengthMeters, double bearingDegrees,
                    int vertexIndex) {
            this.point = point;
            this.distanceAlongMeters = distanceAlongMeters;
            this.lengthMeters = lengthMeters;
            this.bearingDegrees = bearingDegrees;
            this.vertexIndex = vertexIndex;
        }

        public GeoPoint getPoint() { return point; }
        public double getDistanceAlongMeters() { return distanceAlongMeters; }
        public double getLengthMeters() { return lengthMeters; }
        /** Travel direction at this point (NaN if unknown). */
        public double getBearingDegrees() { return bearingDegrees; }
        /** Index of the polyline vertex that starts the segment containing this sample. */
        public int getVertexIndex() { return vertexIndex; }
    }

    private final double stepMeters;

    public RouteSampler(double stepMeters) {
        if (stepMeters <= 0) throw new IllegalArgumentException("Sampling step must be positive.");
        this.stepMeters = stepMeters;
    }

    public List<SamplePoint> sample(List<GeoPoint> line) {
        if (line == null || line.isEmpty()) {
            return Collections.emptyList();
        }
        int segmentCount = line.size() - 1;
        double[] cumulative = new double[line.size()];
        for (int i = 0; i < segmentCount; i++) {
            cumulative[i + 1] = cumulative[i] + GeoMath.distanceMeters(line.get(i), line.get(i + 1));
        }
        double total = cumulative[segmentCount];
        List<SamplePoint> samples = new ArrayList<>();
        if (total <= 0) {
            samples.add(new SamplePoint(line.get(0), 0, 0, Double.NaN, 0));
            return samples;
        }

        int pieces = (int) Math.ceil(total / stepMeters);
        int segment = 0;
        for (int k = 0; k < pieces; k++) {
            double start = k * stepMeters;
            double end = Math.min(total, (k + 1) * stepMeters);
            double middle = (start + end) / 2.0;
            // Move forward to the segment containing the middle distance.
            while (segment < segmentCount - 1 && cumulative[segment + 1] < middle) {
                segment++;
            }
            GeoPoint a = line.get(segment);
            GeoPoint b = line.get(segment + 1);
            double segmentLength = cumulative[segment + 1] - cumulative[segment];
            double fraction = segmentLength == 0 ? 0 : (middle - cumulative[segment]) / segmentLength;
            double bearing = segmentLength == 0 ? Double.NaN : GeoMath.bearingDegrees(a, b);
            samples.add(new SamplePoint(GeoMath.interpolate(a, b, fraction), middle, end - start, bearing, segment));
        }
        return samples;
    }
}
