package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
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

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp,
                                            @NonNull ToDoubleFunction<Assignment> objective) {
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
        val variable = selectVariable(csp, assignment);
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
        return csp.getDomain(variable).stream()
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> search(csp, next, objective, incumbent));
    }

    private static Variable<?> selectVariable(ConstraintSatisfactionProblem csp,
                                               Assignment assignment) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> assignment.getValue(e.getKey()).isEmpty())
                .min(Comparator.comparingInt(e -> e.getValue().size()))
                .map(java.util.Map.Entry::getKey)
                .orElseThrow();
    }
}
