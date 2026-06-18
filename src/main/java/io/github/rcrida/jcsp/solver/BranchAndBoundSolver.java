package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * An optimization solver that applies branch-and-bound pruning. Whenever the cost of the current
 * partial assignment meets or exceeds the best complete solution found so far (the
 * <em>incumbent</em>), the branch is cut immediately.
 *
 * <p>Returns a stream of improving complete assignments (each strictly better than the previous);
 * the last element is the global optimum found within the search.
 *
 * <p>The objective is supplied at construction time by {@link Solver.Factory#createSolver(ConstraintSatisfactionProblem, ToDoubleFunction)},
 * so it must return a lower bound on the cost of any completion of a partial assignment.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BranchAndBoundSolver extends SolverDecorator {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;
    @NonNull ToDoubleFunction<Assignment> objective;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        return search(csp, Assignment.empty(), incumbent);
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       double[] incumbent) {
        if (objective.applyAsDouble(assignment) >= incumbent[0]) {
            return Stream.empty();
        }
        if (assignment.isComplete(csp)) {
            double cost = objective.applyAsDouble(assignment);
            incumbent[0] = cost;
            log.info("Found improving solution with cost {}: {}", cost, assignment);
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return searchValues(variable, csp, assignment, incumbent);
    }

    /**
     * Wildcard-capture helper: binds {@code T} so that {@code getDomain(variable)} and
     * {@code withValue(variable, value)} share the same type argument, avoiding an unchecked cast.
     */
    private <T> Stream<Assignment> searchValues(Variable<T> variable,
                                                 ConstraintSatisfactionProblem csp,
                                                 Assignment assignment,
                                                 double[] incumbent) {
        return domainValuesOrderer.order(csp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> inference.apply(csp, variable, next).stream()
                        .flatMap(c -> search(c, next, incumbent)));
    }
}
