package io.github.rcrida.jcsp.consistency.inverse;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.InverseConstraint;

/**
 * Applies propagation for all {@link InverseConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class InverseConsistency extends FixpointConsistency {
    public static final InverseConsistency INSTANCE = new InverseConsistency();
    private InverseConsistency() { super(InverseConstraint.class); }
}
