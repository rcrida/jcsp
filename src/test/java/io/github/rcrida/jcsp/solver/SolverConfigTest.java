package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SolverConfigTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    @Test
    void nogoodLearningDisabled_solvesWithoutRecordingNogoods() {
        // x=1 fails (y's domain wiped), x=2 succeeds -- covers Solver.Factory's
        // learningEnabled() == false branch (wrapping FULL_PROPAGATION_INFERENCE via
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
        assertThat(solution.get().getStatistics().getNogoodsLearned()).isZero();
    }

    @Test
    void nogoodLearningEnabled_recordsNogoodsThroughTheFactoryChain() {
        // The counterpart to the disabled test above, and load-bearing for coverage since learning
        // stopped being the default: Solver.Factory's reason-tracking Inference is now reached only
        // when a caller opts in. Pigeonhole (3 variables, 2 values, all-different) is unsatisfiable
        // and forces repeated domain wipeouts, so explanations are actually derived.
        Variable<Integer> p = VF.create("scp1");
        Variable<Integer> q = VF.create("scp2");
        Variable<Integer> r = VF.create("scp3");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(p, IntRangeDomain.of(1, 2))
                .variableDomain(q, IntRangeDomain.of(1, 2))
                .variableDomain(r, IntRangeDomain.of(1, 2))
                .allDiffConstraint(java.util.Set.of(p, q, r))
                .build();
        SolverConfig config = SolverConfig.builder().nogoodLearningEnabled(true).build();

        assertThat(Solver.Factory.INSTANCE.createSolver(csp, config).getSolution()).isEmpty();
    }

    /**
     * Builds a CSP whose {@code GlobalCardinalityConstraint} is infeasible (no variable can supply
     * the tracked value) while every counted variable stays non-singleton, which is exactly when
     * that constraint declines to explain itself: {@code allSingletonReason} needs singletons, and
     * {@code RangeNogoodConstraint#fromCurrentBounds} needs gapless domains. {@code counted}
     * chooses between the two, deciding which fallback tier the chain lands on.
     */
    private static ConstraintSatisfactionProblem unexplainableCsp(Variable<Integer> a, Variable<Integer> b,
                                                                  boolean gapless) {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(a, gapless ? IntRangeDomain.of(1, 3) : NumericDiscreteDomain.of(1, 3))
                .variableDomain(b, gapless ? IntRangeDomain.of(1, 3) : NumericDiscreteDomain.of(1, 3))
                .globalCardinalityConstraint(java.util.Set.of(a, b), java.util.Map.of(5, 1))
                .build();
    }

    /**
     * Gapless domains: the propagator declines, so {@code FixpointConsistency} falls back to citing
     * the failing constraint's whole scope as a range. Driven through the factory's own reason
     * -deriving {@link Inference} directly rather than a solve, since this CSP is infeasible at
     * preprocessing and search would never run.
     */
    @Test
    void unexplainedWipeout_gaplessDomains_fallsBackToARangeCitation() {
        Variable<Integer> a = VF.create("gcgap_a");
        Variable<Integer> b = VF.create("gcgap_b");
        ConstraintSatisfactionProblem csp = unexplainableCsp(a, b, true);

        var result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(
                csp, a, Assignment.of(java.util.Map.of(a, 1)));

        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
    }

    /**
     * Gapped domains: the range citation declines too, so the chain reaches its last resort and
     * forbids the whole current assignment.
     */
    @Test
    void unexplainedWipeout_gappedDomains_fallsBackToTheWholeAssignment() {
        Variable<Integer> a = VF.create("gcgapped_a");
        Variable<Integer> b = VF.create("gcgapped_b");
        ConstraintSatisfactionProblem csp = unexplainableCsp(a, b, false);

        var result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(
                csp, a, Assignment.of(java.util.Map.of(a, 1)));

        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.reason().getVariables()).containsExactly(a);
    }

    @Test
    void nogoodLearning_defaultsToUnsetAndResolvesOff() {
        // Unset is distinct from an explicit false: the raw accessor reports what the caller asked
        // for (nothing), while learningEnabled() reports what the library will actually do.
        SolverConfig unset = SolverConfig.builder().build();
        assertThat(unset.getNogoodLearningEnabled()).isNull();
        assertThat(unset.learningEnabled()).isFalse();
    }

    @Test
    void explicitNogoodLearningChoiceOverridesTheLibraryDefault() {
        assertThat(SolverConfig.builder().nogoodLearningEnabled(true).build().learningEnabled()).isTrue();
        assertThat(SolverConfig.builder().nogoodLearningEnabled(false).build().learningEnabled()).isFalse();
    }

    @Test
    void listener_defaultsToNone() {
        assertThat(SolverConfig.builder().build().getListener()).isSameAs(SolverListener.NONE);
    }

    @Test
    void restartRandomization_defaultsToRandomAndVariesPerConfig() {
        RestartRandomization first = SolverConfig.builder().build().getRestartRandomization();
        RestartRandomization second = SolverConfig.builder().build().getRestartRandomization();

        assertThat(first).isNotSameAs(RestartRandomization.NONE);
        assertThat(first.randomFor(1)).isNotNull();
        // Two separately-built default configs get independent base seeds, so their restart-1
        // draws are (with overwhelming probability) different -- confirms this isn't a shared sentinel.
        assertThat(first.randomFor(1).nextLong()).isNotEqualTo(second.randomFor(1).nextLong());
    }

    @Test
    void restartRandomization_canBeOverriddenToNone() {
        assertThat(SolverConfig.builder().restartRandomization(RestartRandomization.NONE).build().getRestartRandomization())
                .isSameAs(RestartRandomization.NONE);
    }
}
