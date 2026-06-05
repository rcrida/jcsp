package io.github.rcrida.jcsp.consistency.alldiff;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;

/**
 * Applies propagation for all {@link AllDiffConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class AllDiffConsistency extends FixpointConsistency {
    public static final AllDiffConsistency INSTANCE = new AllDiffConsistency();
    private AllDiffConsistency() { super(AllDiffConstraint.class); }
}
