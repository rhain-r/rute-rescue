package com.rutarescue.risk;

import com.rutarescue.model.route.SampleVerdict;

/**
 * One rule of the structure-aware risk policy (DOCX section 8).
 *
 * Polymorphism: RiskEngine keeps an ordered list of RiskRule objects and asks each one in
 * turn. The first rule that applies decides the verdict. Adding a new rule means adding a
 * new class, not editing a long if/else chain.
 */
public interface RiskRule {

    /** Short name shown in tests and debugging, e.g. "BridgeRule". */
    String getName();

    boolean appliesTo(SampleFacts facts);

    SampleVerdict evaluate(SampleFacts facts);
}
