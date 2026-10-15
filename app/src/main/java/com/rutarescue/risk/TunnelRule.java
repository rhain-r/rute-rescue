package com.rutarescue.risk;

import com.rutarescue.model.hazard.HazardClass;
import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructure;

/**
 * Tunnels and underpasses (DOCX section 8): "A tunnel intersecting a flood zone is
 * avoided/red unless authoritative evidence verifies it open." Water collects in
 * underpasses, so a flooded tunnel is treated as HIGH for ranking.
 */
public final class TunnelRule implements RiskRule {

    @Override
    public String getName() {
        return "TunnelRule";
    }

    @Override
    public boolean appliesTo(SampleFacts facts) {
        return facts.getStructure() == RoadStructure.TUNNEL;
    }

    @Override
    public SampleVerdict evaluate(SampleFacts facts) {
        HazardClass ground = facts.getGroundHazard();
        if (ground == HazardClass.NONE) {
            return SampleVerdict.builder(getName(), RiskLevel.GREEN, "Tunnel outside mapped flood areas")
                    .structure(RoadStructure.TUNNEL)
                    .build();
        }
        if (facts.isStructureVerifiedOpen()) {
            return SampleVerdict.builder(getName(), RiskLevel.GREEN,
                            "Tunnel verified open by the LGU; " + ground.getLabel() + " hazard is mapped here")
                    .ground(ground)
                    .structure(RoadStructure.TUNNEL)
                    .build();
        }
        return SampleVerdict.builder(getName(), RiskLevel.RED,
                        "Avoid: tunnel/underpass crosses a mapped " + ground.getLabel() + " flood area")
                .ground(ground)
                .effective(HazardClass.HIGH)
                .structure(RoadStructure.TUNNEL)
                .build();
    }
}
