package io.github.rcrida.jcsp.solver.backtrackingsearch;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.Solver;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

/**
 * Implements the backtracking search algorithm for solving constraint satisfaction
 * problems (CSP). Backtracking search is a fundamental algorithmic approach for
 * systematically exploring partial assignments of variables to find solutions that
 * satisfy all constraints.
 *
 * This class employs the following configurable components to guide the backtracking process:
 *
 * - {@link UnassignedVariableSelector}: Determines the strategy for selecting the next
 *   variable to assign when searching for solutions.
 * - {@link DomainValuesOrderer}: Defines the strategy for ordering the domain values of the
 *   selected variable to optimize the search process.
 * - {@link Inference}: Enables optional inference mechanisms to prune the search space
 *   by enforcing consistency constraints after assigning a variable.
 * <p>
 * The {@code searchStream} method provides a stream-based API for generating solutions,
 * supporting lazy evaluation of solutions as they are found during the search.
 */
@Slf4j
@Value
public class BacktrackingSearch implements Solver {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;

    @Override
    public Stream<Assignment> getSolutions(ConstraintSatisfactionProblem csp) {
        return searchStream(csp, Assignment.EMPTY);
    }

    private Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp, Assignment assignment) {
        log.debug("Searching with assignment: {}", assignment);
        if (assignment.isComplete(csp)) {
            log.info("Found solution: {}", assignment);
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> inference.apply(csp, variable, next).stream()
                        .flatMap(c -> searchStream(c, next)));
    }
}
