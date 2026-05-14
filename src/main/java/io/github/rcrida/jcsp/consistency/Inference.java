package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Optional;

/**
 * Interface for inference algorithms in constraint satisfaction problems. The inference algorithm adds a global constraint for
 * the new variable assignment and imposes arc-, path-, or k-consistency constraints as desired.
 */
@FunctionalInterface
public interface Inference {
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment);
}
