package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * A functional interface that defines a strategy for ordering the domain values of a variable
 * in the context of a constraint satisfaction problem (CSP). The order in which domain values
 * are considered can significantly impact the efficiency of solving the CSP.
 * <p>
 * Implementations of this interface typically apply heuristics to prioritize domain values
 * in a way that reduces the search space or satisfies constraints more effectively.
 */
@FunctionalInterface
public interface DomainValuesOrderer {
    Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> variable, @NonNull Assignment assignment);
}
