package com.rutarescue.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bucket index of rings by bounding box, for fast point-in-polygon checks. */
final class RingIndex {

    private static final double BUCKET = 0.002;

    private final List<Ring> rings;
    private final Map<Long, List<Integer>> buckets = new HashMap<>();

    RingIndex(List<Ring> rings) {
        this.rings = rings;
        for (int i = 0; i < rings.size(); i++) {
            Ring ring = rings.get(i);
            for (int x = bucket(ring.minX); x <= bucket(ring.maxX); x++) {
                for (int y = bucket(ring.minY); y <= bucket(ring.maxY); y++) {
                    buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(i);
                }
            }
        }
    }

    List<Integer> candidates(double x, double y) {
        List<Integer> list = buckets.get(key(bucket(x), bucket(y)));
        return list == null ? Collections.emptyList() : list;
    }

    /** Even-odd test against ALL rings of the class (outer rings and holes together). */
    boolean contains(double x, double y) {
        int crossings = 0;
        for (int index : candidates(x, y)) {
            Ring ring = rings.get(index);
            if (ring.bboxContains(x, y)) crossings += ring.crossingsRightOf(x, y);
        }
        return (crossings & 1) == 1;
    }

    private static int bucket(double value) {
        return (int) Math.floor(value / BUCKET);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
