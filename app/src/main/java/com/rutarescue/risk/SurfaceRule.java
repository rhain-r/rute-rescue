package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructure;

/**
 * Default rule: a surface road inherits the NOAH class of the selected scenario
 * (DOCX section 8). Points outside the prepared coverage are never shown as green.
 */
public final class SurfaceRule implements RiskRule {

    @Override
    public String getName() {
        return "SurfaceRule";
    }

    @Override
    public boolean appliesTo(SampleFacts facts) {
        return true; // fallback rule, always last in the chain
    }

    @Override
    public SampleVerdict evaluate(SampleFacts facts) {
        if (!facts.isCovered()) {
            return SampleVerdict.builder(getName(), RiskLevel.AMBER,
                            "Outside the prepared NOAH coverage - no hazard data here")
                    .structure(RoadStructure.SURFACE)
                    .outOfCoverage(true)
                    .build();
        }
        HazardClass ground = facts.getGroundHazard();
        RiskLevel level;
        switch (ground) {
            case HIGH:
                level = RiskLevel.RED;
                break;
            case MEDIUM:
            case LOW:
                level = RiskLevel.AMBER;
                break;
            default:
                level = RiskLevel.GREEN;
        }
        String reason = ground == HazardClass.NONE
                ? "Surface road with no mapped hazard"
                : "Surface road over " + ground.getLabel() + " mapped hazard (" + ground.getDepthRange() + ")";
        return SampleVerdict.builder(getName(), level, reason)
                .ground(ground)
                .effective(ground)
                .structure(RoadStructure.SURFACE)
                .build();
    }
}
