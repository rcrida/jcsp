package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NullConflictExplainerTest {

    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void explain_alwaysReturnsEmpty() {
        Variable<Integer> x = F.create("x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .build();
        var result = NullConflictExplainer.INSTANCE.explain(csp, x, Assignment.of(Map.of(x, 1)));
        assertThat(result).isEmpty();
    }

    /**
     * n=8 queens: with the default conflict explainer, backtracking search learns nogoods
     * (verified empirically -- unlike most other sizes, which this solver resolves by
     * propagation alone with zero backtracks); wiring {@link NullConflictExplainer#INSTANCE} in
     * via {@link SolverConfig} instead keeps {@code nogoodsLearned} at zero for the whole search,
     * even though the same domain wipeouts still occur (and backtracks still happen) -- only the
     * explanation step is skipped.
     */
    @SuppressWarnings("unchecked")
    private static ConstraintSatisfactionProblem nQueens(int n) {
        Variable<Integer>[] queens = new Variable[n];
        for (int i = 0; i < n; i++) queens[i] = F.create("nce-q" + i);
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

    @Test
    void solverConfig_defaultExplainer_learnsNogoods() {
        var csp = nQueens(8);
        var solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution().orElseThrow();
        assertThat(solution.getStatistics().getNogoodsLearned().get()).isGreaterThan(0);
    }

    @Test
    void solverConfig_nullConflictExplainer_learnsNoNogoodsDespiteBacktracking() {
        var csp = nQueens(8);
        var config = SolverConfig.builder().conflictExplainer(NullConflictExplainer.INSTANCE).build();
        var solution = Solver.Factory.INSTANCE.createSolver(csp, config).getSolution().orElseThrow();
        assertThat(solution.getStatistics().getBacktracks().get()).isGreaterThan(0);
        assertThat(solution.getStatistics().getNogoodsLearned().get()).isZero();
    }
}
