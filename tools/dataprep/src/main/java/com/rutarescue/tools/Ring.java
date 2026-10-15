package com.rutarescue.tools;

/**
 * One closed polygon ring (longitude = x, latitude = y) with its bounding box and signed
 * area. In shapefiles, outer rings are clockwise (negative signed area) and holes are
 * counter-clockwise (positive signed area).
 */
final class Ring {

    final double[] xs;
    final double[] ys;
    final double minX;
    final double minY;
    final double maxX;
    final double maxY;
    final double signedArea;

    Ring(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 4) {
            throw new IllegalArgumentException("A ring needs at least 4 points (closed).");
        }
        this.xs = xs;
        this.ys = ys;
        double loX = Double.MAX_VALUE, loY = Double.MAX_VALUE, hiX = -Double.MAX_VALUE, hiY = -Double.MAX_VALUE;
        double area = 0;
        for (int i = 0; i < xs.length; i++) {
            loX = Math.min(loX, xs[i]);
            loY = Math.min(loY, ys[i]);
            hiX = Math.max(hiX, xs[i]);
            hiY = Math.max(hiY, ys[i]);
            int j = (i + 1) % xs.length;
            area += xs[i] * ys[j] - xs[j] * ys[i];
        }
        this.minX = loX;
        this.minY = loY;
        this.maxX = hiX;
        this.maxY = hiY;
        this.signedArea = area / 2.0;
    }

    int size() {
        return xs.length;
    }

    boolean isClockwise() {
        return signedArea < 0;
    }

    double absArea() {
        return Math.abs(signedArea);
    }

    boolean bboxContains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    /** Even-odd ray casting, using the same edge test as the rasterizer. */
    int crossingsRightOf(double x, double y) {
        int count = 0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            double y1 = ys[j], y2 = ys[i];
            if ((y1 > y) != (y2 > y)) {
                double xi = xs[j] + (y - y1) * (xs[i] - xs[j]) / (y2 - y1);
                if (x < xi) count++;
            }
        }
        return count;
    }
}
