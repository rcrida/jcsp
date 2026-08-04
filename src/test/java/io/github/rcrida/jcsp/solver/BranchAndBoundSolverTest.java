package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // ── Cancellation ─────────────────────────────────────────────────────────

    @Test
    void cancellationDuringInference_convertsToEmptyStream_notThrown() {
        // Simulates SolverCancelledException surfacing from deep inside FixpointPropagation's
        // "between propagators" check (reached via inference.applyWithReason) -- searchValues must
        // catch this and convert it to Stream.empty(), never letting it propagate (this class has no
        // distinct getSolution() algorithm of its own to legitimately let it through).
        Inference alwaysCancels = new Inference() {
            @Override
            public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem c, Variable<?> variable, Assignment assignment) {
                throw new SolverCancelledException(assignment.getStatistics());
            }

            @Override
            public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem c, Variable<?> variable, Assignment assignment) {
                throw new SolverCancelledException(assignment.getStatistics());
            }
        };
        BranchAndBoundSolver cancelling = BranchAndBoundSolver.builder()
                .objective(BranchAndBoundSolverTest::sum)
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(alwaysCancels)
                .build();

        assertThat(cancelling.getSolutions(CSP).toList()).isEmpty();
    }

    @Test
    void cancellationStopsOptimizationStream_withoutThrowing() {
        var cancellation = new Cancellation();
        cancellation.cancel();
        BranchAndBoundSolver cancelled = BranchAndBoundSolver.builder()
                .objective(BranchAndBoundSolverTest::sum)
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference((problem, variable, assignment) -> Optional.of(problem))
                .cancellation(cancellation)
                .build();

        assertThat(cancelled.getSolutions(CSP).findFirst()).isEmpty();
        assertThat(cancelled.getSolution(CSP)).isEmpty();
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

    // ── SolverListener ───────────────────────────────────────────────────────

    @Test
    void listenerReceivesOnIncumbentImprovedForEachImprovingSolution() {
        var costs = new CopyOnWriteArrayList<Double>();
        SolverListener recorder = new SolverListener() {
            @Override
            public void onIncumbentImproved(Assignment solution, double cost) {
                costs.add(cost);
            }
        };

        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(BranchAndBoundSolverTest::sum)
                .unassignedVariableSelector(MinimumRemainingValuesSelector.INSTANCE)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference((problem, variable, assignment) -> Optional.of(problem))
                .listener(recorder)
                .build();

        val improving = solver.getSolutions(CSP).toList();

        assertThat(costs).hasSameSizeAs(improving);
        for (int i = 0; i < improving.size(); i++) {
            assertThat(costs.get(i)).isEqualTo((double) sum(improving.get(i)));
        }
        for (int i = 1; i < costs.size(); i++) {
            assertThat(costs.get(i)).isLessThan(costs.get(i - 1));
        }
    }

    // ── LP relaxation pruning (ADR-0009) ────────────────────────────────────

    // x,y,z in [0,3] (max sum 9), sum(x,y,z) >= 8, minimize x+y+z -> true optimum 8.
    // Branching x=0 or x=1 first (fixed order) leaves y+z<=6, which can't reach the remaining
    // 8 or 7 needed -- the LP relaxation is infeasible there even though the plain
    // objective.applyAsDouble(partial) check (x alone contributes 0 or 1) wouldn't prune it,
    // exercising isPruned's LP-infeasible branch. x=2/x=3 branches are LP-feasible with a bound
    // of exactly 8, so once the first cost-8 solution sets the incumbent, later same-bound
    // branches get pruned by the dominance check (bound >= incumbent) too.
    private static final Variable<Integer> LP_X = F.create("lp_x");
    private static final Variable<Integer> LP_Y = F.create("lp_y");
    private static final Variable<Integer> LP_Z = F.create("lp_z");

    private static final ConstraintSatisfactionProblem LP_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(LP_X, IntRangeDomain.of(0, 3))
            .variableDomain(LP_Y, IntRangeDomain.of(0, 3))
            .variableDomain(LP_Z, IntRangeDomain.of(0, 3))
            .sumConstraint(Set.of(LP_X, LP_Y, LP_Z), Operator.GEQ, 8)
            .build();

    private static io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector fixedOrder(
            Variable<Integer> x, Variable<Integer> y, Variable<Integer> z) {
        return (csp, assignment) -> java.util.stream.Stream.of(x, y, z)
                .filter(v -> assignment.getValue(v).isEmpty())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Narrows just-assigned {@code variable} to an {@link io.github.rcrida.jcsp.domains.AssignedDomain}
     * -- the same construct {@code MAC} uses for search-time domain narrowing -- and nothing else, so
     * {@code csp.getDomain(v)} (what {@code LpModelBuilder} reads its box bounds from) reflects search
     * decisions without conflating in any constraint-propagator pruning of its own.
     */
    @SuppressWarnings("unchecked")
    private static Inference narrowAssignedToSingleton() {
        return (csp, variable, assignment) -> Optional.of(csp.toBuilder()
                .variableDomain((Variable<Object>) variable,
                        new io.github.rcrida.jcsp.domains.AssignedDomain(assignment.getValues().get(variable)))
                .build());
    }

    private static BranchAndBoundSolver lpSolver(ToDoubleFunction<Assignment> objective) {
        return BranchAndBoundSolver.builder()
                .objective(objective)
                .unassignedVariableSelector(fixedOrder(LP_X, LP_Y, LP_Z))
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference(narrowAssignedToSingleton())
                .statistics(new io.github.rcrida.jcsp.assignments.Statistics())
                .build();
    }

    @Test
    void linearObjective_prunesInfeasibleAndDominatedBranches_findsCorrectOptimum() {
        LinearObjective linearObjective = LinearObjective.builder()
                .coefficient(LP_X, 1.0).coefficient(LP_Y, 1.0).coefficient(LP_Z, 1.0)
                .build();

        // Exhausts the whole stream (not just getSolution()'s first element) so search continues
        // past the first found solution (x=2,y=3,z=3, cost 8) into the x=3 branch, whose own LP
        // bound (8) now equals the incumbent (8) -- exercising the dominance-prune ("bound >=
        // incumbent") branch, not just the earlier LP-infeasible-subtree branch (x=0/x=1).
        BranchAndBoundSolver solver = lpSolver(linearObjective);
        var improving = solver.getSolutions(LP_CSP).toList();

        assertThat(improving).hasSize(1);
        assertThat(linearObjective.applyAsDouble(improving.get(0))).isCloseTo(8.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void linearObjective_explorestFewerNodesThanPlainObjective() {
        LinearObjective linearObjective = LinearObjective.builder()
                .coefficient(LP_X, 1.0).coefficient(LP_Y, 1.0).coefficient(LP_Z, 1.0)
                .build();
        // Same real cost function, but not `instanceof LinearObjective` -- exercises isPruned's
        // plain (non-LP) branch for an apples-to-apples comparison.
        ToDoubleFunction<Assignment> plainObjective = linearObjective::applyAsDouble;

        BranchAndBoundSolver withLp = lpSolver(linearObjective);
        BranchAndBoundSolver withoutLp = lpSolver(plainObjective);

        var lpSolutions = withLp.getSolutions(LP_CSP).toList();
        var plainSolutions = withoutLp.getSolutions(LP_CSP).toList();

        assertThat(lpSolutions).isNotEmpty();
        assertThat(plainSolutions).isNotEmpty();
        assertThat(linearObjective.applyAsDouble(lpSolutions.getLast())).isEqualTo(8.0);
        assertThat(plainObjective.applyAsDouble(plainSolutions.getLast())).isEqualTo(8.0);
        assertThat(withLp.getStatistics().getNodesExplored().get())
                .isLessThan(withoutLp.getStatistics().getNodesExplored().get());
    }
}
