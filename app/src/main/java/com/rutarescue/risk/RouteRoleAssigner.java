package com.rutarescue.risk;

import com.rutarescue.model.route.RouteAssessment;
import com.rutarescue.model.route.RouteRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Names the ranked routes for the Phase 1 route cards.
 *
 * The first route of the DOCX ranking (RouteRanker) is "Lowest risk". Among the others, the
 * fastest one is "Most direct" and the next one is "Balanced", but only when they save at least
 * a minute compared with the lowest-risk route; otherwise they are plain "Alternative" routes.
 * The risk ranking itself never changes here.
 */
public final class RouteRoleAssigner {

    /** A route must be at least this much faster than the lowest-risk one to earn a name. */
    static final long MIN_SAVING_SECONDS = 60;

    /** @param ranked routes already sorted by RouteRanker (first = lowest risk). */
    public List<RouteRole> assign(List<RouteAssessment> ranked) {
        List<RouteRole> roles = new ArrayList<>(Collections.nCopies(ranked.size(), RouteRole.ALTERNATIVE));
        if (ranked.isEmpty()) return roles;
        if (ranked.size() == 1) {
            roles.set(0, RouteRole.ONLY_ROUTE);
            return roles;
        }
        roles.set(0, RouteRole.LOWEST_RISK);
        long lowestRiskTime = ranked.get(0).getCandidate().getTravelTimeSeconds();

        int fastest = -1;
        for (int i = 1; i < ranked.size(); i++) {
            if (fastest == -1 || time(ranked, i) < time(ranked, fastest)) fastest = i;
        }
        if (time(ranked, fastest) + MIN_SAVING_SECONDS <= lowestRiskTime) roles.set(fastest, RouteRole.MOST_DIRECT);

        for (int i = 1; i < ranked.size(); i++) {
            if (i != fastest && time(ranked, i) + MIN_SAVING_SECONDS <= lowestRiskTime) {
                roles.set(i, RouteRole.BALANCED);
                break; // one "Balanced" card: the best-ranked remaining route
            }
        }
        return roles;
    }

    private static long time(List<RouteAssessment> ranked, int index) {
        return ranked.get(index).getCandidate().getTravelTimeSeconds();
    }
}
