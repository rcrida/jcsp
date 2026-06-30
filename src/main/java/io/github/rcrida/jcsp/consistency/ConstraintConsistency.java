package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;
import java.util.Optional;

/**
 * A single consistency/propagation pass over a {@link ConstraintSatisfactionProblem}.
 * Returns the reduced problem, or {@link Optional#empty()} if infeasibility is detected.
 */
@FunctionalInterface
public interface ConstraintConsistency {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp);

    /**
     * Re-runs this consistency pass with reason tracking and returns the nogood that explains
     * the conflict, or {@link Optional#empty()} if this pass did not detect a conflict.
     * The default returns empty; subclasses that support explanation override this.
     */
    default Optional<Map<Variable<?>, Object>> explainConflict(ConstraintSatisfactionProblem csp) {
        return Optional.empty();
    }
}
