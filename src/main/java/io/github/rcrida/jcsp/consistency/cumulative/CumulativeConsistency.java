package io.github.rcrida.jcsp.consistency.cumulative;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;

/**
 * Applies propagation for all {@link CumulativeConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class CumulativeConsistency extends FixpointConsistency {
    public static final CumulativeConsistency INSTANCE = new CumulativeConsistency();
    private CumulativeConsistency() { super(CumulativeConstraint.class); }
}
