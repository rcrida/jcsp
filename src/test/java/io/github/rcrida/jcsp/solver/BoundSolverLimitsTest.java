package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundSolverLimitsTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    /**
     * 8-queens: minimum constraint-graph degree = 7 == targetTreewidth, so TreeDecompositionSolver
     * early-exits and CutsetConditioning's complexity check also rejects decomposition — both
     * fall through to DomWdegLubySearch.getSolutions(), where limits apply.
     */
    @SuppressWarnings("unchecked")
    private static ConstraintSatisfactionProblem satisfiable() {
        int n = 8;
        Variable<Integer>[] queens = new Variable[n];
        for (int i = 0; i < n; i++) queens[i] = VF.create("q" + i);
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> q : queens) builder.variableDomain(q, IntRangeDomain.of(1, n));
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                final int diff = j - i;
                builder.notEqualsConstraint(queens[i], queens[j]);
                builder.biPredicateConstraint(queens[i], queens[j],
                        (a, b) -> Math.abs((int) a - (int) b) != diff);
            }
        }
        return builder.build();
    }

    private static ConstraintSatisfactionProblem unsatisfiable() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
    }

    @Test
    void getSolutionThrowsWhenNodeLimitExceeded() {
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().limits(SolverLimits.ofNodes(1)).build());

        assertThatThrownBy(solver::getSolution)
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .isNotNull();
    }

    @Test
    void getSolutionThrowsWhenTimeLimitExceeded() {
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().limits(SolverLimits.ofTime(Duration.ofNanos(1))).build());

        assertThatThrownBy(solver::getSolution)
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .isNotNull();
    }

    @Test
    void getSolutionReturnsEmptyForGenuineUnsat() {
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(unsatisfiable());

        assertThat(solver.getSolution()).isEmpty();
    }

    @Test
    void getSolutionsStreamTruncatesSilentlyOnNodeLimit() {
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(),
                SolverConfig.builder().limits(SolverLimits.ofNodes(1)).build());

        assertThat(solver.getSolutions().findFirst()).isEmpty();
    }

    @Test
    void resetAllowsSubsequentCallToDetectLimit() {
        SolverConfig config = SolverConfig.builder().limits(SolverLimits.ofNodes(1)).build();
        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(satisfiable(), config);

        assertThatThrownBy(solver::getSolution).isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(solver::getSolution).isInstanceOf(LimitExceededException.class);
    }
}
