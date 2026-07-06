package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

public class BranchAndBoundSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");
    static final Variable<Integer> Z = F.create("z");

    // Minimise x+y+z subject to allDiff; domain {1..5}.
    // Only one optimal solution: {x=1, y=2, z=3} (or permutations) with sum=6.
    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 5))
            .variableDomain(Y, IntRangeDomain.of(1, 5))
            .variableDomain(Z, IntRangeDomain.of(1, 5))
            .allDiffConstraint(java.util.Set.of(X, Y, Z))
            .build();

    static int sum(Assignment a) {
        return a.getValue(X).orElse(0) + a.getValue(Y).orElse(0) + a.getValue(Z).orElse(0);
    }

    static BranchAndBoundSolver solver(ToDoubleFunction<Assignment> objective) {
        return solver(objective, SolverLimits.unlimited());
    }

    static BranchAndBoundSolver solver(ToDoubleFunction<Assignment> objective, SolverLimits limits) {
        return BranchAndBoundSolver.builder()
                .objective(objective)
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference((problem, variable, assignment) -> Optional.of(problem))
                .limits(limits)
                .build();
    }

    @Test
    void optimize_findsMinimumSum() {
        val result = solver(a -> sum(a)).getSolution(CSP);
        assertThat(result).isPresent();
        assertThat(sum(result.get())).isEqualTo(6);
    }

    @Test
    void getSolutions_returnsImprovingStream() {
        val improving = solver(a -> sum(a)).getSolutions(CSP).toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(sum(improving.get(i))).isLessThan(sum(improving.get(i - 1)));
        }
        assertThat(sum(improving.getLast())).isEqualTo(6);
    }

    @Test
    void earlyTermination_returnsApproximateSolution() {
        val first = solver(a -> sum(a)).getSolutions(CSP).findFirst();
        assertThat(first).isPresent();
        assertThat(sum(first.get())).isLessThanOrEqualTo(12);
    }

    // ── Limits ────────────────────────────────────────────────────────────────

    @Test
    void nodeLimitStopsOptimizationStream() {
        val result = solver(a -> sum(a), SolverLimits.ofNodes(1)).getSolutions(CSP).findFirst();
        assertThat(result).isEmpty();
    }

    @Test
    void timeLimitStopsOptimizationStream() {
        val result = solver(a -> sum(a), SolverLimits.ofTime(Duration.ofNanos(1))).getSolutions(CSP).findFirst();
        assertThat(result).isEmpty();
    }
}
