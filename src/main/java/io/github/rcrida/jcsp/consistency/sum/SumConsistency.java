package io.github.rcrida.jcsp.consistency.sum;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;

/**
 * Applies propagation for all {@link SumConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class SumConsistency extends FixpointConsistency {
    public static final SumConsistency INSTANCE = new SumConsistency();
    private SumConsistency() { super(SumConstraint.class); }
}
