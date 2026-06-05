package io.github.rcrida.jcsp.consistency.among;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;

/**
 * Applies propagation for all {@link AmongConstraint} instances,
 * via {@link io.github.rcrida.jcsp.consistency.FixpointConsistency}.
 */
public final class AmongConsistency extends FixpointConsistency {
    public static final AmongConsistency INSTANCE = new AmongConsistency();
    private AmongConsistency() { super(AmongConstraint.class); }
}
