package com.rutarescue.tools;

import java.util.Arrays;
import java.util.List;

/**
 * Scanline rasterizer: fills every grid cell whose centre lies inside the polygons of a
 * hazard class (even-odd rule, so holes are handled automatically). When classes overlap,
 * the higher class wins. A cell centre (x, y) is inside exactly when the ray casting test
 * in Ring.crossingsRightOf() says so, which the control-point gate verifies.
 */
final class Rasterizer {

    private Rasterizer() {
    }

    static void fillClass(byte[] codes, GridSpec grid, List<Ring> rings, int classCode) {
        DoubleList[] rowXs = new DoubleList[grid.rows];
        for (Ring ring : rings) {
            int n = ring.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double x1 = ring.xs[j], y1 = ring.ys[j];
                double x2 = ring.xs[i], y2 = ring.ys[i];
                if (y1 == y2) continue;
                double low = Math.min(y1, y2);
                double high = Math.max(y1, y2);
                int rowStart = Math.max(0, (int) Math.floor((low - grid.minLat) / grid.cell - 0.5) - 1);
                int rowEnd = Math.min(grid.rows - 1, (int) Math.ceil((high - grid.minLat) / grid.cell - 0.5) + 1);
                for (int row = rowStart; row <= rowEnd; row++) {
                    double y = grid.centerY(row);
                    if ((y1 > y) != (y2 > y)) {
                        double xi = x1 + (y - y1) * (x2 - x1) / (y2 - y1);
                        if (rowXs[row] == null) rowXs[row] = new DoubleList();
                        rowXs[row].add(xi);
                    }
                }
            }
        }
        for (int row = 0; row < grid.rows; row++) {
            DoubleList list = rowXs[row];
            if (list == null || list.size() < 2) continue;
            double[] xs = list.toSortedArray();
            for (int k = 0; k + 1 < xs.length; k += 2) {
                int start = firstColumnAtOrAfter(grid, xs[k]);
                int end = firstColumnAtOrAfter(grid, xs[k + 1]); // exclusive
                for (int col = Math.max(0, start); col < Math.min(grid.cols, end); col++) {
                    int index = row * grid.cols + col;
                    if (codes[index] < classCode) codes[index] = (byte) classCode;
                }
            }
        }
    }

    /** Smallest column whose centre x is >= the given x. */
    private static int firstColumnAtOrAfter(GridSpec grid, double x) {
        int col = (int) Math.floor((x - grid.minLon) / grid.cell - 0.5);
        while (grid.centerX(col) < x) col++;
        while (grid.centerX(col - 1) >= x) col--;
        return col;
    }

    /** Packs one class code per cell into 2 bits per cell (4 cells per byte). */
    static byte[] pack(byte[] codes) {
        byte[] packed = new byte[(codes.length + 3) / 4];
        for (int i = 0; i < codes.length; i++) {
            packed[i >>> 2] |= (byte) ((codes[i] & 3) << ((i & 3) * 2));
        }
        return packed;
    }

    /** Small growable list of doubles (avoids boxing millions of Double objects). */
    static final class DoubleList {
        private double[] values = new double[8];
        private int size;

        void add(double value) {
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }

        int size() {
            return size;
        }

        double[] toSortedArray() {
            double[] copy = Arrays.copyOf(values, size);
            Arrays.sort(copy);
            return copy;
        }
    }
}
