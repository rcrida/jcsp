package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.consistency.Inference;
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

    @Test
    void statisticsRemainReadableWhenNodeLimitLeavesNoImprovingSolution() {
        io.github.rcrida.jcsp.assignments.Statistics statistics = new io.github.rcrida.jcsp.assignments.Statistics();
        BranchAndBoundSolver limited = BranchAndBoundSolver.builder()
                .objective(BranchAndBoundSolverTest::sum)
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference((problem, variable, assignment) -> Optional.of(problem))
                .limits(SolverLimits.ofNodes(1))
                .statistics(statistics)
                .build();

        assertThat(limited.getSolutions(CSP).findFirst()).isEmpty();

        // The Statistics field is seeded into the root Assignment, so it's readable via this same
        // live reference even though the node limit meant no complete Assignment (improving or
        // otherwise) was ever returned.
        assertThat(statistics.getNodesExplored().get()).isGreaterThan(0);
    }

    // ── Nogood learning ──────────────────────────────────────────────────────

    // x=1 is tried first (fixed selector order, ascending values) and fails deterministically --
    // notEqualsConstraint(x, y) wipes y's singleton domain -- before x=2 succeeds. Mirrors
    // DomWdegLubySearchTest#nogoodLearningDisabled_solvesWithoutRecordingNogoods, adapted to
    // BranchAndBoundSolver's own (non dom/wdeg) variable/value ordering.
    private static ConstraintSatisfactionProblem deterministicFailThenSucceedCsp(
            Variable<Integer> x, Variable<Integer> y, Variable<Integer> w1, Variable<Integer> w2) {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .variableDomain(w1, IntRangeDomain.of(5, 5))
                .variableDomain(w2, IntRangeDomain.of(6, 6))
                .notEqualsConstraint(x, y)
                .notEqualsConstraint(x, w1)
                .notEqualsConstraint(x, w2)
                .build();
    }

    private static io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector fixedOrder(
            Variable<Integer> x, Variable<Integer> y, Variable<Integer> w1, Variable<Integer> w2) {
        return (csp, assignment) -> java.util.stream.Stream.of(x, y, w1, w2)
                .filter(v -> assignment.getValue(v).isEmpty())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void nogoodsLearnedStatisticIncrementsOnFailedBranch() {
        Variable<Integer> x = F.create("bbx");
        Variable<Integer> y = F.create("bby");
        Variable<Integer> w1 = F.create("bbw1");
        Variable<Integer> w2 = F.create("bbw2");
        ConstraintSatisfactionProblem csp = deterministicFailThenSucceedCsp(x, y, w1, w2);

        NogoodStore store = new NogoodStore();
        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(a -> 0)
                .unassignedVariableSelector(fixedOrder(x, y, w1, w2))
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        Optional<Assignment> solution = solver.getSolution(csp);

        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(x).orElseThrow()).isEqualTo(2);
        assertThat(solution.get().getStatistics().getNogoodsLearned().get()).isGreaterThan(0);
        assertThat(solution.get().getStatistics().getBacktracks().get()).isGreaterThan(0);
        assertThat(store.size()).isGreaterThan(0);
    }

    @Test
    void nogoodLearningDisabled_solvesWithoutRecordingNogoods() {
        Variable<Integer> x = F.create("bbnlx");
        Variable<Integer> y = F.create("bbnly");
        Variable<Integer> w1 = F.create("bbnlw1");
        Variable<Integer> w2 = F.create("bbnlw2");
        ConstraintSatisfactionProblem csp = deterministicFailThenSucceedCsp(x, y, w1, w2);

        NogoodStore store = new NogoodStore();
        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(a -> 0)
                .unassignedVariableSelector(fixedOrder(x, y, w1, w2))
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(Inference.withoutReasonTracking(Solver.Factory.FULL_PROPAGATION_INFERENCE))
                .nogoodStore(store)
                .build();

        Optional<Assignment> solution = solver.getSolution(csp);

        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(x).orElseThrow()).isEqualTo(2);
        assertThat(solution.get().getStatistics().getNogoodsLearned().get()).isZero();
        assertThat(solution.get().getStatistics().getBacktracks().get()).isGreaterThan(0);
        assertThat(store.apply(csp)).isEqualTo(csp);
    }
}
