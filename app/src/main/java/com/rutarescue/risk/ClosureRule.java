package com.rutarescue.risk;

import com.rutarescue.model.route.RiskLevel;
import com.rutarescue.model.route.SampleVerdict;
import com.rutarescue.model.structure.RoadStructureRecord;

/**
 * Rule 1: explicit closure evidence beats everything else.
 *  - an active LGU-reported closure,
 *  - a bridge/tunnel whose status is CLOSED ("explicitly closed or impassable"),
 *  - a road closure reported in the provider's live traffic sections.
 */
public final class ClosureRule implements RiskRule {

    @Override
    public String getName() {
        return "ClosureRule";
    }

    @Override
    public boolean appliesTo(SampleFacts facts) {
        RoadStructureRecord record = facts.getStructureRecord();
        boolean structureClosed = record != null && record.isClosed()
                && facts.getStructureMatch().getType() == record.getType();
        return facts.getClosure() != null || facts.isProviderClosure() || structureClosed;
    }

    @Override
    public SampleVerdict evaluate(SampleFacts facts) {
        String reason;
        if (facts.getClosure() != null) {
            reason = "Closed: " + facts.getClosure().getName() + " (LGU report: " + facts.getClosure().getReason() + ")";
        } else if (facts.getStructureRecord() != null && facts.getStructureRecord().isClosed()) {
            reason = "Closed " + facts.getStructure().getLabel().toLowerCase() + ": " + facts.structureName()
                    + " is marked impassable by the LGU";
        } else {
            reason = "Road closure reported in live traffic data";
        }
        return SampleVerdict.builder(getName(), RiskLevel.UNAVAILABLE, reason)
                .ground(facts.getGroundHazard())
                .effective(facts.getGroundHazard())
                .structure(facts.getStructure())
                .closed(true)
                .build();
    }
}
