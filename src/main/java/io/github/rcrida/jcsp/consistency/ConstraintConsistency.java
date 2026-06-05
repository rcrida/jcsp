package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;

import java.util.Optional;

/**
 * A single consistency/propagation pass over a {@link ConstraintSatisfactionProblem}.
 * Returns the reduced problem, or {@link Optional#empty()} if infeasibility is detected.
 */
@FunctionalInterface
public interface ConstraintConsistency {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp);
}
