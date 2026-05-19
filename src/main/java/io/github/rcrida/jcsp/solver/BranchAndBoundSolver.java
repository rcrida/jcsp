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
 * A terminal solver that overrides {@link Solver#getSolutions(ConstraintSatisfactionProblem, ToDoubleFunction)}
 * with branch-and-bound pruning. Whenever the cost of the current partial assignment meets or
 * exceeds the best complete solution found so far (the <em>incumbent</em>), the branch is cut
 * immediately.
 *
 * <p>{@link #getSolutions(ConstraintSatisfactionProblem)} performs plain backtracking (no
 * objective) by delegating to {@code inner}.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BranchAndBoundSolver extends SolverDecorator {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull ToDoubleFunction<Assignment> objective) {
        log.info("Search space before branch-and-bound = {}", csp.getSearchSpace());
        double[] incumbent = {Double.MAX_VALUE};
        return search(csp, Assignment.empty(), objective, incumbent);
    }

    private Stream<Assignment> search(ConstraintSatisfactionProblem csp,
                                       Assignment assignment,
                                       ToDoubleFunction<Assignment> objective,
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
        return searchValues(variable, csp, assignment, objective, incumbent);
    }

    /**
     * Wildcard-capture helper: binds {@code T} so that {@code getDomain(variable)} and
     * {@code withValue(variable, value)} share the same type argument, avoiding an unchecked cast.
     */
    private <T> Stream<Assignment> searchValues(Variable<T> variable,
                                                 ConstraintSatisfactionProblem csp,
                                                 Assignment assignment,
                                                 ToDoubleFunction<Assignment> objective,
                                                 double[] incumbent) {
        return domainValuesOrderer.order(csp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> inference.apply(csp, variable, next).stream()
                        .flatMap(c -> search(c, next, objective, incumbent)));
    }

}
