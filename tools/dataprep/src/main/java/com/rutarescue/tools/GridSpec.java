package com.rutarescue.tools;

/**
 * The fixed grid shared by all three scenarios: 0.0001 degree cells (about 11 m) over a
 * box that contains the whole supplied Metro Manila coverage.
 */
final class GridSpec {

    final double minLon;
    final double minLat;
    final double cell;
    final int cols;
    final int rows;

    GridSpec(double minLon, double minLat, double cell, int cols, int rows) {
        this.minLon = minLon;
        this.minLat = minLat;
        this.cell = cell;
        this.cols = cols;
        this.rows = rows;
    }

    static GridSpec metroManila() {
        return new GridSpec(120.90, 14.35, 0.0001, 2400, 4400);
    }

    double maxLon() {
        return minLon + cols * cell;
    }

    double maxLat() {
        return minLat + rows * cell;
    }

    double centerX(int col) {
        return minLon + (col + 0.5) * cell;
    }

    double centerY(int row) {
        return minLat + (row + 0.5) * cell;
    }
}
