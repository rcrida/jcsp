package io.github.rcrida.jcsp.consistency.linear;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;

/**
 * Applies propagation for all {@link LinearConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class LinearConsistency extends FixpointConsistency {
    public static final LinearConsistency INSTANCE = new LinearConsistency();
    private LinearConsistency() { super(LinearConstraint.class); }
}
