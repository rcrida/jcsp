package io.github.rcrida.jcsp.solver.backtrackingsearch;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BacktrackingSearchTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    private BacktrackingSearch solver() {
        return BacktrackingSearch.builder()
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build();
    }

    @Test
    void findsSolutionsWithBacktracking() {
        // biPredicateConstraint is not propagated by inference, so partial assignments
        // that violate x+y=4 are caught by isConsistent (filter branch = false).
        // Also covers: isComplete→false (recurse) and isComplete→true (solution found).
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .biPredicateConstraint(x, y, (a, b) -> (int) a + (int) b == 4)
                .build();

        // (1,3), (2,2), (3,1) satisfy x+y=4
        assertThat(solver().getSolutions(csp).toList()).hasSize(3);
    }

    @Test
    void tracksBacktracksOnConsistencyFailure() {
        // No-op inference prevents AC3 from pruning values, so the filter's
        // isConsistent() == false branch is exercised and backtracks are counted.
        var noInference = BacktrackingSearch.builder()
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference((csp, variable, assignment) -> Optional.of(csp))
                .build();
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .biPredicateConstraint(x, y, (a, b) -> (int) a + (int) b == 4)
                .build();

        var solutions = noInference.getSolutions(csp).toList();
        assertThat(solutions).hasSize(3);
        assertThat(solutions.get(0).getStatistics().getBacktracks().get()).isPositive();
    }

    @Test
    void returnsEmptyStreamForUnsatisfiableCSP() {
        // Inference detects domain wipeout for y after assigning x=1, making the
        // inner flatMap produce an empty stream — covers the inference-failure path.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        assertThat(solver().getSolutions(csp)).isEmpty();
    }

    // ── Limits ────────────────────────────────────────────────────────────────

    @Test
    void nodeLimitStopsStream() {
        // x+y=4 with domains {1..3}: solutions are (1,3),(2,2),(3,1) — needs multiple nodes.
        // A node limit of 1 prevents any solution from being found.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .biPredicateConstraint(x, y, (a, b) -> (int) a + (int) b == 4)
                .build();

        BacktrackingSearch limited = BacktrackingSearch.builder()
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofNodes(1))
                .build();

        assertThat(limited.getSolutions(csp).findFirst()).isEmpty();
    }

    @Test
    void timeLimitStopsStream() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .biPredicateConstraint(x, y, (a, b) -> (int) a + (int) b == 4)
                .build();

        BacktrackingSearch limited = BacktrackingSearch.builder()
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofTime(Duration.ofNanos(1)))
                .build();

        assertThat(limited.getSolutions(csp).findFirst()).isEmpty();
    }
}
