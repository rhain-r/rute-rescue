package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.util.GeoMath;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RouteSamplerTest {

    @Test
    public void samplesCoverTheWholeLengthInFixedSteps() {
        GeoPoint a = new GeoPoint(14.6, 121.0);
        GeoPoint b = new GeoPoint(14.6, 121.001);
        double total = GeoMath.distanceMeters(a, b);
        List<RouteSampler.SamplePoint> samples = new RouteSampler(10).sample(Arrays.asList(a, b));
        assertEquals((int) Math.ceil(total / 10), samples.size());
        double sum = 0;
        for (RouteSampler.SamplePoint sample : samples) sum += sample.getLengthMeters();
        assertEquals(total, sum, 0.001);
        assertEquals(90.0, samples.get(0).getBearingDegrees(), 0.5);
    }

    @Test
    public void singlePointRouteGivesOneZeroLengthSample() {
        List<RouteSampler.SamplePoint> samples = new RouteSampler(10)
                .sample(Arrays.asList(new GeoPoint(14.6, 121.0), new GeoPoint(14.6, 121.0)));
        assertEquals(1, samples.size());
        assertEquals(0, samples.get(0).getLengthMeters(), 0);
    }
}
