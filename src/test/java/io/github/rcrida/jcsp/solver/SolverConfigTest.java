package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SolverConfigTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    @Test
    void nogoodLearningDisabled_solvesWithoutRecordingNogoods() {
        // x=1 fails (y's domain wiped), x=2 succeeds -- covers Solver.Factory's
        // isNogoodLearningEnabled() == false branch (wrapping FULL_PROPAGATION_INFERENCE via
        // Inference#withoutReasonTracking) through the full public createSolver(csp, config) path.
        Variable<Integer> x = VF.create("scx");
        Variable<Integer> y = VF.create("scy");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        BoundSolver solver = Solver.Factory.INSTANCE.createSolver(csp,
                SolverConfig.builder().nogoodLearningEnabled(false).build());

        var solution = solver.getSolution();

        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(x).orElseThrow()).isEqualTo(2);
        assertThat(solution.get().getStatistics().getNogoodsLearned().get()).isZero();
    }

    @Test
    void nogoodLearningEnabled_isDefault() {
        assertThat(SolverConfig.builder().build().isNogoodLearningEnabled()).isTrue();
    }
}
