package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructure;

/**
 * The bridge correction (DOCX section 8). The legacy code let a bridge inherit the red
 * flood class of the ground below it. Instead:
 *  - a bridge does NOT automatically inherit the flood depth below it;
 *  - with no verified passability over a flood zone it is AMBER, "Bridge passability unverified";
 *  - with fresh verified-open evidence it may be GREEN, still mentioning the hazard below;
 *  - a closed bridge never reaches this rule because ClosureRule runs first (RED/unavailable).
 */
public final class BridgeRule implements RiskRule {

    public static final String UNVERIFIED_MESSAGE = "Bridge passability unverified";

    @Override
    public String getName() {
        return "BridgeRule";
    }

    @Override
    public boolean appliesTo(SampleFacts facts) {
        return facts.getStructure() == RoadStructure.BRIDGE;
    }

    @Override
    public SampleVerdict evaluate(SampleFacts facts) {
        HazardClass below = facts.getGroundHazard();
        if (below == HazardClass.NONE) {
            return SampleVerdict.builder(getName(), RiskLevel.GREEN, "Bridge with no mapped hazard below")
                    .structure(RoadStructure.BRIDGE)
                    .build();
        }
        if (facts.isStructureVerifiedOpen()) {
            return SampleVerdict.builder(getName(), RiskLevel.GREEN,
                            "Bridge verified open by the LGU (" + facts.structureName() + "); "
                                    + below.getLabel() + " hazard mapped below")
                    .ground(below)
                    .structure(RoadStructure.BRIDGE)
                    .build();
        }
        return SampleVerdict.builder(getName(), RiskLevel.AMBER,
                        UNVERIFIED_MESSAGE + " (" + facts.structureName() + "; " + below.getLabel()
                                + " hazard mapped below)")
                .ground(below)
                .effective(HazardClass.NONE)
                .structure(RoadStructure.BRIDGE)
                .unverifiedStructure(true)
                .build();
    }
}
