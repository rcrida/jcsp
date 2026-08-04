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
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ── Fractional-variable branching (ADR-0009 Phase 3) ────────────────────

    @Test
    void mostFractionalLpVariable_branchedBeforeConfiguredSelectorsChoice() {
        // minimize x+y s.t. x+2y>=5, x,y in [0,3]: y is twice as "efficient" against the
        // constraint, so the LP optimum is x=0, y=2.5 (cost 2.5) -- y is fractional, x isn't.
        // fixedOrder always tries x first, so seeing y visited first proves fractional selection
        // overrode the configured selector rather than falling back to it.
        Variable<Integer> x = F.create("frac_x");
        Variable<Integer> y = F.create("frac_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 3))
                .variableDomain(y, IntRangeDomain.of(0, 3))
                .linearConstraint(java.util.Map.of(x, 1, y, 2), Operator.GEQ, 5)
                .build();
        LinearObjective linearObjective = LinearObjective.builder()
                .coefficient(x, 1.0).coefficient(y, 1.0)
                .build();

        var visitOrder = new java.util.ArrayList<Variable<?>>();
        io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer recording =
                (c, variable, assignment) -> {
                    visitOrder.add(variable);
                    return DefaultValueOrderer.INSTANCE.order(c, variable, assignment);
                };

        io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector xThenY =
                (c, assignment) -> assignment.getValue(x).isEmpty() ? x : y;

        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(linearObjective)
                .unassignedVariableSelector(xThenY) // x tried before y when not overridden
                .domainValuesOrderer(recording)
                .inference(narrowAssignedToSingleton())
                .build();

        var solution = solver.getSolution(csp);

        assertThat(solution).isPresent();
        assertThat(visitOrder.get(0)).isEqualTo(y);
    }

    @Test
    void allIntegralLpSolution_fallsBackToConfiguredSelector() {
        // Every vertex of {x,y,z in [0,3], sum>=8} that minimizes x+y+z is already all-integer
        // (e.g. x=2,y=3,z=3), so no LP-covered variable is fractional -- selectFractionalVariable
        // finds no candidate and falls back to unassignedVariableSelector, which fixedOrder pins to
        // x first.
        LinearObjective linearObjective = LinearObjective.builder()
                .coefficient(LP_X, 1.0).coefficient(LP_Y, 1.0).coefficient(LP_Z, 1.0)
                .build();
        var visitOrder = new java.util.ArrayList<Variable<?>>();
        io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer recording =
                (c, variable, assignment) -> {
                    visitOrder.add(variable);
                    return DefaultValueOrderer.INSTANCE.order(c, variable, assignment);
                };

        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(linearObjective)
                .unassignedVariableSelector(fixedOrder(LP_X, LP_Y, LP_Z))
                .domainValuesOrderer(recording)
                .inference(narrowAssignedToSingleton())
                .build();

        var solution = solver.getSolution(LP_CSP);

        assertThat(solution).isPresent();
        assertThat(visitOrder.get(0)).isEqualTo(LP_X);
    }

    // ── Continuous residual resolution (ADR-0009 Phase 4) ───────────────────
    // These go through Solver.Factory (not a hand-built BranchAndBoundSolver) so real propagation
    // narrows domains the way production use does -- resolveContinuousResidual's LP fast path and
    // bisection fallback both depend on that, unlike the LP-pruning/fractional-branching tests above
    // which deliberately isolate BranchAndBoundSolver with a minimal custom Inference.

    @Test
    void purelyContinuousLinearObjective_resolvedViaLpFastPath() {
        // minimize x+y s.t. x+y>=5, x,y in [0,10] -- both covered by the objective and the linear
        // constraint, so the LP fill is complete and consistent: accepted without ever falling back
        // to bisection.
        Variable<Double> x = F.create("residual_x");
        Variable<Double> y = F.create("residual_y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .linearConstraint(java.util.Map.of(x, 1.0, y, 1.0), Operator.GEQ, 5.0)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).coefficient(y, 1.0).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        assertThat(objective.applyAsDouble(solution.get())).isCloseTo(5.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    // Both tests below use a single continuous variable and an inequality predicate deliberately:
    // BisectionConditioningSolver's own re-propagation loop only re-runs SumBound/SumVariable/
    // LinearBound/LinearVariable propagators (the same four LpModelBuilder recognises) -- any
    // constraint that makes the LP fill wrong is, by construction, also invisible to bisection's own
    // narrowing, so it only gets checked once every variable is fully singleton. With two jointly
    // constrained variables and no propagation help, that's an exponential blind grid search; worse,
    // an EQ constraint on a genuinely nonlinear relationship (e.g. x*y=12) is essentially impossible
    // to land on exactly via independent-axis bisection to a fixed epsilon at all (confirmed: an
    // earlier two-variable, product-EQ version of this test ran for 554s and returned no solution).
    // A single variable with an inequality predicate avoids both problems: one variable bisects in
    // ~14 steps regardless, and an inequality defines an open region bisection can actually land in.

    @Test
    void lpFillInconsistentWithNonLpVisibleConstraint_fallsBackToBisection() {
        // minimize x (no linear constraint on it at all) s.t. x>=5 via a predicateConstraint
        // (invisible to LpModelBuilder). The LP fill picks x's box minimum (1), which violates the
        // predicate -- complete but inconsistent, rejected, falling back to bisection, which
        // converges to the true constrained optimum x=5.
        Variable<Double> x = F.create("residual_px");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(1.0, 10.0))
                .predicateConstraint(x, v -> v >= 5.0)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        assertThat(xVal).isCloseTo(5.0, org.assertj.core.api.Assertions.within(1e-2));
    }

    @Test
    void lpFillIncomplete_variableNotLpCovered_fallsBackToBisection() {
        // x isn't in the objective (a constant 0) or any linear constraint, so LpModelBuilder's
        // relevantVariables is empty and the LP fill leaves x completely unassigned (incomplete,
        // rejected before consistency is even checked) -- falling back to bisection to find any
        // point satisfying x>=5.
        Variable<Double> x = F.create("residual_qx");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(1.0, 10.0))
                .predicateConstraint(x, v -> v >= 5.0)
                .build();
        LinearObjective objective = LinearObjective.builder().constant(0.0).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        double xVal = (Double) solution.get().getValue(x).orElseThrow();
        assertThat(xVal).isGreaterThanOrEqualTo(5.0);
    }

    @Test
    void continuousResidualGenuinelyInfeasible_returnsEmpty() {
        // x>=15 is unsatisfiable within [1,10] -- both the LP fill and the bisection fallback fail,
        // so resolveContinuousResidual (and therefore resolveComplete) returns nothing at all.
        Variable<Double> x = F.create("residual_infeasible_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(1.0, 10.0))
                .predicateConstraint(x, v -> v >= 15.0)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isEmpty();
    }

    @Test
    void continuousResidualAlreadySingletonFromPropagation_extractedNotLost() {
        // Regression test for a real bug caught in code review: BisectionConditioningSolver.getSolutions
        // has a top-level shortcut (findWidestBounded(csp) == null -> delegate straight to inner)
        // separate from the isFullyDetermined() check deeper in allFeasible -- it fires whenever
        // there's nothing left to bisect at all, which is the common case where propagation already
        // collapsed the continuous residual to a singleton, not just a rare/defensive one. An earlier
        // version of resolveContinuousResidual used a Stream.empty() inner here, silently losing a
        // trivially-available solution whenever this shortcut fired.
        //
        // n in {1,2,3} (discrete, via NumericDiscreteDomain<Double> so it can share a linear
        // constraint with x), x in [0,10] (continuous), n+x=5. n never gets a chance to be the
        // "widest bounded" variable at all -- the moment n is assigned, the SAME inference step that
        // narrows n's own domain to a singleton also propagates x down to a singleton via n+x=5,
        // before x is ever selected through the ordinary branching path. Not a LinearObjective --
        // this forces resolveContinuousResidual straight to the bisection fallback, whose top-level
        // shortcut then fires immediately since x is already singleton.
        Variable<Double> n = F.create("residual_singleton_n");
        Variable<Double> x = F.create("residual_singleton_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(n, NumericDiscreteDomain.of(1.0, 2.0, 3.0))
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .linearConstraint(java.util.Map.of(n, 1.0, x, 1.0), Operator.EQ, 5.0)
                .build();
        ToDoubleFunction<Assignment> objective = a -> a.getValue(n).map(v -> (Double) v).orElse(0.0);

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(n)).contains(1.0);
        assertThat(solution.get().getValue(x)).contains(4.0);
    }

    @Test
    void mixedDiscreteAndContinuous_discreteDecidedBeforeContinuousResolved() {
        // n in {1,2,3} with n>=2 (discrete), x in [0,10] with x>=3 (continuous). True optimum:
        // n=2, x=3, cost=5 -- only reachable if n is decided (branched) before x is resolved.
        Variable<Integer> n = F.create("residual_n");
        Variable<Double> x = F.create("residual_mixed_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(n, IntRangeDomain.of(1, 3))
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .sumConstraint(Set.of(n), Operator.GEQ, 2)
                .linearConstraint(java.util.Map.of(x, 1.0), Operator.GEQ, 3.0)
                .build();
        LinearObjective objective = LinearObjective.builder().coefficient(n, 1.0).coefficient(x, 1.0).build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        assertThat(objective.applyAsDouble(solution.get())).isCloseTo(5.0, org.assertj.core.api.Assertions.within(1e-6));
    }

    @Test
    void unassignedVariableSelector_violatingDiscreteFirstContract_failsFast() {
        // A custom selector that always picks the continuous variable, even while the discrete one
        // remains unassigned, violates BranchAndBoundSolver's documented contract. requireDiscrete
        // should catch this with a clear IllegalStateException rather than letting domainValuesOrderer
        // crash confusingly trying to enumerate a non-singleton BoundedDomain.
        Variable<Integer> n = F.create("contract_n");
        Variable<Double> x = F.create("contract_x");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(n, IntRangeDomain.of(1, 3))
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .build();
        io.github.rcrida.jcsp.solver.backtrackingsearch.selector.UnassignedVariableSelector alwaysX =
                (c, assignment) -> x;
        BranchAndBoundSolver solver = BranchAndBoundSolver.builder()
                .objective(a -> 0.0)
                .unassignedVariableSelector(alwaysX)
                .domainValuesOrderer(DefaultValueOrderer.INSTANCE)
                .inference((problem, variable, assignment) -> Optional.of(problem))
                .build();

        assertThatThrownBy(() -> solver.getSolutions(csp).toList())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unassignedVariableSelector")
                .hasMessageContaining("contract_x");
    }
}
