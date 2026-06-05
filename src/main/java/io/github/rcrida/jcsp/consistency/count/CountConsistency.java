package io.github.rcrida.jcsp.consistency.count;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;

/**
 * Applies propagation for all {@link CountConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class CountConsistency extends FixpointConsistency {
    public static final CountConsistency INSTANCE = new CountConsistency();
    private CountConsistency() { super(CountConstraint.class); }
}
