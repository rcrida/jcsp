package org.jcsp.consistency;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.unary.UnaryValueConstraint;
import org.jcsp.variables.Variable;

import java.util.Optional;

/**
 * Interface for inference algorithms in constraint satisfaction problems. The inference algorithm adds a global constraint for
 * the new variable assignment and imposes arc-, path-, or k-consistency constraints as desired.
 */
@FunctionalInterface
public interface Inference {
    default Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable variable, Assignment assignment) {
        val value = assignment.getValue(variable).orElseThrow();
        val moreCsp = problem.toBuilder().constraint(UnaryValueConstraint.of(variable, value)).build();
        return apply(moreCsp);
    }

    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem);
}
