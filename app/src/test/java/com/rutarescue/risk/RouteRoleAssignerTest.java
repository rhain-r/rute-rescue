package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteRole;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Phase 1 route cards: Lowest risk (first in the DOCX ranking), Balanced and Most direct. */
public class RouteRoleAssignerTest {

    private final RouteRoleAssigner assigner = new RouteRoleAssigner();

    @Test
    public void aSingleRouteIsTheOnlyRoute() {
        RouteAssessment only = RouteTestData.route(0, 1000, 600).build();
        assertEquals(Collections.singletonList(RouteRole.ONLY_ROUTE), assigner.assign(Collections.singletonList(only)));
    }

    @Test
    public void firstRankedIsLowestRiskThenFastestIsMostDirectThenBalanced() {
        RouteAssessment lowestRisk = RouteTestData.route(0, 3400, 41 * 60).build();
        RouteAssessment balanced = RouteTestData.route(1, 3000, 33 * 60).hazard(HazardClass.MEDIUM, 500, 620).build();
        RouteAssessment direct = RouteTestData.route(2, 2600, 28 * 60).hazard(HazardClass.HIGH, 800, 1150).build();
        List<RouteRole> roles = assigner.assign(Arrays.asList(lowestRisk, balanced, direct));
        assertEquals(Arrays.asList(RouteRole.LOWEST_RISK, RouteRole.BALANCED, RouteRole.MOST_DIRECT), roles);
    }

    @Test
    public void routesThatAreNotFasterThanTheLowestRiskOneAreOnlyAlternatives() {
        RouteAssessment lowestRisk = RouteTestData.route(0, 2000, 600).build();
        RouteAssessment slower = RouteTestData.route(1, 2500, 800).hazard(HazardClass.LOW, 0, 100).build();
        RouteAssessment slowest = RouteTestData.route(2, 2600, 900).hazard(HazardClass.MEDIUM, 0, 100).build();
        List<RouteRole> roles = assigner.assign(Arrays.asList(lowestRisk, slower, slowest));
        assertEquals(Arrays.asList(RouteRole.LOWEST_RISK, RouteRole.ALTERNATIVE, RouteRole.ALTERNATIVE), roles);
    }

    @Test
    public void savingLessThanAMinuteIsNotEnoughToBeMostDirect() {
        RouteAssessment lowestRisk = RouteTestData.route(0, 3100, 2215).build();
        RouteAssessment almostSame = RouteTestData.route(1, 3100, 2190).hazard(HazardClass.HIGH, 0, 10).build();
        assertEquals(Arrays.asList(RouteRole.LOWEST_RISK, RouteRole.ALTERNATIVE),
                assigner.assign(Arrays.asList(lowestRisk, almostSame)));
    }

    @Test
    public void twoRoutesGiveLowestRiskAndMostDirect() {
        RouteAssessment lowestRisk = RouteTestData.route(0, 3000, 900).build();
        RouteAssessment direct = RouteTestData.route(1, 2000, 600).hazard(HazardClass.HIGH, 0, 100).build();
        assertEquals(Arrays.asList(RouteRole.LOWEST_RISK, RouteRole.MOST_DIRECT),
                assigner.assign(Arrays.asList(lowestRisk, direct)));
    }
}
