package io.github.rcrida.jcsp.solver.backtrackingsearch;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.SolverLimits;
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
@Builder
public class BacktrackingSearch implements Solver {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull SolverLimits limits;

    public static class BacktrackingSearchBuilder {
        private SolverLimits limits = SolverLimits.unlimited();
    }

    @Override
    public Stream<Assignment> getSolutions(ConstraintSatisfactionProblem csp) {
        long deadline = limits.deadlineNanos();
        return searchStream(csp, Assignment.empty(), deadline);
    }

    private Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp, Assignment assignment, long deadline) {
        log.debug("Searching with assignment: {}", assignment);
        if (assignment.isComplete(csp)) {
            log.info("Found solution: {}", assignment);
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> {
                    if (limits.isNodeLimitExceeded(next.getStatistics().getNodesExplored().get())
                            || limits.isTimeLimitExceeded(deadline)) {
                        limits.markLimitReached(next.getStatistics());
                        return false;
                    }
                    if (next.isConsistent(csp)) return true;
                    next.getStatistics().incrementBacktracks();
                    return false;
                })
                .flatMap(next -> {
                    var inferred = inference.apply(csp, variable, next);
                    if (inferred.isEmpty()) {
                        next.getStatistics().incrementBacktracks();
                        return Stream.empty();
                    }
                    return searchStream(inferred.get(), next, deadline);
                });
    }
}
