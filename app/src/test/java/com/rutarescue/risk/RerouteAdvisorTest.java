package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RouteAssessment;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Quiet rerouting during guidance: the remaining part of the current route is compared with
 * a fresh route from the resident's position. Only a clear improvement replaces the route.
 */
public class RerouteAdvisorTest {

    private final RerouteAdvisor advisor = new RerouteAdvisor();

    @Test
    public void keepsTheCurrentRouteWhenTheNewOneIsNotBetter() {
        RouteAssessment current = RouteTestData.route(0, 3000, 1800).build();
        RouteAssessment fresh = RouteTestData.route(0, 2000, 1200).build();
        assertEquals(RerouteAdvisor.Reason.NONE, advisor.compare(current, 1000, fresh));
    }

    @Test
    public void switchesWhenAHighHazardIsAheadAndTheNewRouteAvoidsIt() {
        RouteAssessment current = RouteTestData.route(0, 3000, 1800).hazard(HazardClass.HIGH, 2000, 2300).build();
        RouteAssessment fresh = RouteTestData.route(0, 2100, 1300).hazard(HazardClass.MEDIUM, 500, 560).build();
        assertEquals(RerouteAdvisor.Reason.LOWER_RISK, advisor.compare(current, 1000, fresh));
    }

    @Test
    public void ignoresAHazardThatIsAlreadyBehindTheResident() {
        RouteAssessment current = RouteTestData.route(0, 3000, 1800).hazard(HazardClass.HIGH, 200, 500).build();
        RouteAssessment fresh = RouteTestData.route(0, 2000, 1200).build();
        assertEquals(RerouteAdvisor.Reason.NONE, advisor.compare(current, 1000, fresh));
    }

    @Test
    public void switchesForClearlyLessFloodedDistance() {
        RouteAssessment current = RouteTestData.route(0, 3000, 1800).hazard(HazardClass.MEDIUM, 1500, 1800).build();
        RouteAssessment fresh = RouteTestData.route(0, 2000, 1200).hazard(HazardClass.MEDIUM, 300, 400).build();
        assertEquals(RerouteAdvisor.Reason.LOWER_RISK, advisor.compare(current, 1000, fresh));
    }

    @Test
    public void switchesForMuchLessTrafficWhenTheRiskIsTheSame() {
        RouteAssessment current = RouteTestData.route(0, 4000, 2400).build();
        RouteAssessment fresh = RouteTestData.route(0, 3000, 1000).traffic(60).build();
        assertEquals(RerouteAdvisor.Reason.LESS_TRAFFIC, advisor.compare(current, 1000, fresh));
    }

    @Test
    public void neverSwitchesToAClosedRoute() {
        RouteAssessment current = RouteTestData.route(0, 3000, 1800).hazard(HazardClass.HIGH, 2000, 2300).build();
        RouteAssessment fresh = RouteTestData.route(0, 2000, 900).closed().build();
        assertEquals(RerouteAdvisor.Reason.NONE, advisor.compare(current, 1000, fresh));
    }
}
