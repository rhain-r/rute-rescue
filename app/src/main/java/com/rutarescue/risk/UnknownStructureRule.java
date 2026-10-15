package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructure;

/**
 * "Unknown structures do not silently become green" (DOCX section 8). We could not tell
 * whether the traveller is on a bridge or on the road below it, so we conservatively keep
 * the ground hazard and also count the distance as unverified structure.
 */
public final class UnknownStructureRule implements RiskRule {

    @Override
    public String getName() {
        return "UnknownStructureRule";
    }

    @Override
    public boolean appliesTo(SampleFacts facts) {
        return facts.getStructure() == RoadStructure.UNKNOWN;
    }

    @Override
    public SampleVerdict evaluate(SampleFacts facts) {
        HazardClass ground = facts.getGroundHazard();
        RiskLevel level = ground == HazardClass.HIGH ? RiskLevel.RED : RiskLevel.AMBER;
        String reason = ground == HazardClass.NONE
                ? "Road structure could not be confirmed near " + facts.structureName()
                : "Unconfirmed structure near " + facts.structureName() + " over " + ground.getLabel() + " mapped hazard";
        return SampleVerdict.builder(getName(), level, reason)
                .ground(ground)
                .effective(ground)
                .structure(RoadStructure.UNKNOWN)
                .unverifiedStructure(true)
                .build();
    }
}
