package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP).
 * A constraint defines a condition or restriction on the values that
 * a set of variables can take. Implementations of this interface provide
 * the specific logic for evaluating whether an assignment satisfies the constraint.
 */
public interface Constraint {
    boolean isSatisfiedBy(@NonNull Assignment assignment);
    String getRelation();
    Set<Variable<?>> getVariables();
}
