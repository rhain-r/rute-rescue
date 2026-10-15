package com.rutarescue.risk;

import com.rutarescue.model.common.GeoPoint;
import com.rutarescue.model.hazard.FloodScenario;
import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteCandidate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** DOCX section 8 lexicographic ranking: no time saving can outrank a closure or a worse hazard. */
public class RouteRankerTest {

    private final RouteRanker ranker = new RouteRanker();

    private static RouteAssessment route(int index, boolean closed, HazardClass maxHazard, double exposure,
                                         double unverified, long seconds) {
        RouteCandidate candidate = new RouteCandidate(index,
                Arrays.asList(new GeoPoint(14.6, 121.0), new GeoPoint(14.6, 121.01)), 1000, seconds, 0, true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        EnumMap<HazardClass, Double> map = new EnumMap<>(HazardClass.class);
        map.put(maxHazard == HazardClass.NONE ? HazardClass.LOW : maxHazard, exposure);
        return new RouteAssessment(candidate, FloodScenario.FIVE_YEAR, Collections.emptyList(), closed, maxHazard,
                RiskLevel.AMBER, map, unverified, 0, 0, 0, Collections.emptyList());
    }

    @Test
    public void closureLosesEvenWhenMuchFaster() {
        RouteAssessment closedFast = route(0, true, HazardClass.NONE, 0, 0, 300);
        RouteAssessment openSlow = route(1, false, HazardClass.HIGH, 500, 0, 3000);
        List<RouteAssessment> ranked = ranker.rank(Arrays.asList(closedFast, openSlow));
        assertSame(openSlow, ranked.get(0));
    }

    @Test
    public void lowerMaximumHazardBeatsLessExposure() {
        RouteAssessment highShort = route(0, false, HazardClass.HIGH, 20, 0, 600);
        RouteAssessment mediumLong = route(1, false, HazardClass.MEDIUM, 900, 0, 900);
        assertSame(mediumLong, ranker.rank(Arrays.asList(highShort, mediumLong)).get(0));
    }

    @Test
    public void lessExposureThenLessUnverifiedThenFaster() {
        RouteAssessment a = route(0, false, HazardClass.LOW, 300, 0, 600);
        RouteAssessment b = route(1, false, HazardClass.LOW, 100, 50, 900);
        RouteAssessment c = route(2, false, HazardClass.LOW, 100, 0, 1200);
        RouteAssessment d = route(3, false, HazardClass.LOW, 100, 0, 1000);
        List<RouteAssessment> ranked = ranker.rank(Arrays.asList(a, b, c, d));
        assertSame(d, ranked.get(0));
        assertSame(c, ranked.get(1));
        assertSame(b, ranked.get(2));
        assertSame(a, ranked.get(3));
        assertEquals(1, d.getRank());
        assertEquals(RouteAssessment.TOP_LABEL, d.getHeadline());
    }

    @Test
    public void explanationNamesTheDecidingCriterion() {
        RouteAssessment top = route(0, false, HazardClass.LOW, 100, 0, 900);
        RouteAssessment worse = route(1, false, HazardClass.HIGH, 50, 0, 300);
        List<RouteAssessment> ranked = ranker.rank(Arrays.asList(worse, top));
        List<String> lines = new RouteExplainer().explain(ranked, true);
        assertTrue(lines.get(1).contains("reaches high hazard"));
    }

    @Test
    public void singleRouteIsNeverCalledSafer() {
        RouteAssessment only = route(0, false, HazardClass.NONE, 0, 0, 600);
        ranker.rank(Collections.singletonList(only));
        assertTrue(only.getHeadline().startsWith("Only route available"));
        for (String line : new RouteExplainer().explain(Collections.singletonList(only), false)) {
            assertTrue(!line.toLowerCase().contains("safe route"));
        }
    }
}
