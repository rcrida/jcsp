package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rcrida.jcsp.solver.LimitExceededException;

class DomWdegLubySearchTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    // ── Luby sequence ───────────────────────────────────────────────────────

    @Test
    void lubySequenceFirst15Terms() {
        long[] expected = {1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8};
        for (int k = 1; k <= 15; k++) {
            assertThat(DomWdegLubySearch.luby(k))
                    .as("luby(%d)", k)
                    .isEqualTo(expected[k - 1]);
        }
    }

    @Test
    void lubyIsPowerOf2AtRunBoundaries() {
        assertThat(DomWdegLubySearch.luby(1)).isEqualTo(1);
        assertThat(DomWdegLubySearch.luby(3)).isEqualTo(2);
        assertThat(DomWdegLubySearch.luby(7)).isEqualTo(4);
        assertThat(DomWdegLubySearch.luby(15)).isEqualTo(8);
        assertThat(DomWdegLubySearch.luby(31)).isEqualTo(16);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static DomWdegLubySearch solver() {
        return DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build();
    }

    // ── Solving ─────────────────────────────────────────────────────────────

    @Test
    void solvesSimpleTwoVariableCSP() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(x, y)
                .build();

        Optional<Assignment> solution = solver().getSolution(csp);

        assertThat(solution).isPresent();
        int xv = solution.get().getValue(x).orElseThrow();
        int yv = solution.get().getValue(y).orElseThrow();
        assertThat(xv).isNotEqualTo(yv);
    }

    @Test
    void returnsEmptyForUnsatisfiableCSP() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        assertThat(solver().getSolution(csp)).isEmpty();
    }

    @Test
    void statisticsRemainReadableAfterGenuineUnsatViaGetSolution() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        Statistics statistics = new Statistics();
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .statistics(statistics)
                .build();

        assertThat(limited.getSolution(csp)).isEmpty();

        // Genuine UNSAT carries no Statistics in its return value (Optional.empty()) -- the whole
        // point of the shared statistics field is that the caller already holds this same live
        // reference regardless, so it's still readable and reflects real search activity.
        assertThat(statistics.getNodesExplored().get()).isGreaterThan(0);
    }

    @Test
    void statisticsRemainReadableAfterEmptyGetSolutionsStream() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        Statistics statistics = new Statistics();
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .statistics(statistics)
                .build();

        assertThat(limited.getSolutions(csp).findFirst()).isEmpty();

        assertThat(statistics.getNodesExplored().get()).isGreaterThan(0);
    }

    @Test
    void solves4Queens() {
        int n = 4;
        @SuppressWarnings("unchecked")
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

        Optional<Assignment> solution = solver().getSolution(builder.build());

        assertThat(solution).isPresent();
        Assignment a = solution.get();
        List<Integer> placed = List.of(
                a.getValue(queens[0]).orElseThrow(),
                a.getValue(queens[1]).orElseThrow(),
                a.getValue(queens[2]).orElseThrow(),
                a.getValue(queens[3]).orElseThrow());
        assertThat(placed).doesNotHaveDuplicates();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                assertThat(Math.abs(placed.get(i) - placed.get(j))).isNotEqualTo(j - i);
            }
        }
    }

    @Test
    void nogoodLearningDisabled_solvesWithoutRecordingNogoods() {
        // Same x/y/w1/w2 CSP as nogoodsLearnedStatisticIncrementsOnFailedBranch: with
        // DefaultValueOrderer, x=1 fails deterministically (y's domain wiped, a backtrack) before
        // x=2 succeeds -- covering both outcomes of Inference.withoutReasonTracking's wrapped
        // applyWithReason (its own apply() delegate returning empty vs present) in one
        // deterministic run, and confirming nothing ever gets recorded into the store.
        Variable<Integer> x = VF.create("nlx");
        Variable<Integer> y = VF.create("nly");
        Variable<Integer> w1 = VF.create("nlw1");
        Variable<Integer> w2 = VF.create("nlw2");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .variableDomain(w1, IntRangeDomain.of(5, 5))
                .variableDomain(w2, IntRangeDomain.of(6, 6))
                .notEqualsConstraint(x, y)
                .notEqualsConstraint(x, w1)
                .notEqualsConstraint(x, w2)
                .build();

        NogoodStore store = new NogoodStore();
        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer.INSTANCE)
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

    @Test
    void solvesWithTightBudgetViaRestarts() {
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .notEqualsConstraint(x, y)
                .build();

        DomWdegLubySearch tightSolver = DomWdegLubySearch.builder()
                .lubyUnit(1)
                .maxRestarts(512)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build();

        assertThat(tightSolver.getSolution(csp)).isPresent();
    }

    @Test
    void exhaustsAllRestartsWhenBudgetAlwaysExceeded() {
        // x=1 and y=1 forced, x≠y → every MAC attempt fails immediately.
        // With lubyUnit=1, maxRestarts=1: first restart exceeds budget on the first inference
        // failure, the loop exits, and getSolution returns empty after exhausting all restarts.
        // Covers: BudgetExceeded thrown, catch block, loop exhaustion, log.warn path.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        DomWdegLubySearch extremelyTightSolver = DomWdegLubySearch.builder()
                .lubyUnit(1)
                .maxRestarts(1)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build();

        assertThat(extremelyTightSolver.getSolution(csp)).isEmpty();
    }

    @Test
    void backtrackingWhenInferenceCannotDetectDeadEnd() {
        // N-ary predicate constraints are not propagated by MAC/inference, so
        // searchOne must recurse into dead-end subtrees and backtrack.
        // Specifically: x=1 leads to a subtree with no solution (1+?+?≠6 for
        // y,z∈{1,2}), so searchOne returns empty and the outer loop must
        // continue to x=2 — covering the result.isPresent() → false branch.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        Variable<Integer> z = VF.create("z");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .variableDomain(z, IntRangeDomain.of(1, 2))
                .predicateConstraint(Set.of(x, y, z), a ->
                        (int) a.getValue(x).orElseThrow()
                      + (int) a.getValue(y).orElseThrow()
                      + (int) a.getValue(z).orElseThrow() == 6)
                .build();

        assertThat(solver().getSolution(csp)).hasValueSatisfying(a -> {
            assertThat((int) a.getValue(x).orElseThrow()).isEqualTo(2);
            assertThat((int) a.getValue(y).orElseThrow()).isEqualTo(2);
            assertThat((int) a.getValue(z).orElseThrow()).isEqualTo(2);
        });
    }

    // ── Nogood learning ───────────────────────────────────────────────────────

    @Test
    void prepopulatedNogoodPrunesBranchDuringGetSolutions() {
        // x ∈ {1,2}, y ∈ {1,2}, x≠y → two solutions: (1,2) and (2,1).
        // Pre-loading nogood {x=1} prunes the x=1 branch entirely, leaving only (2,1).
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .build();

        NogoodStore store = new NogoodStore();
        store.record(GroundNogoodConstraint.of(java.util.Map.of(x, 1)));

        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        List<Assignment> solutions = solver.getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(x).orElseThrow()).isEqualTo(2);
    }

    @Test
    void prepopulatedNogoodsBlockAllBranchesDuringGetSolution() {
        // x ∈ {1,2}, y ∈ {1,2}, x≠y — nogoods blocking both root choices make problem
        // appear unsolvable even though the CSP itself is satisfiable.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .build();

        NogoodStore store = new NogoodStore();
        store.record(GroundNogoodConstraint.of(java.util.Map.of(x, 1)));
        store.record(GroundNogoodConstraint.of(java.util.Map.of(x, 2)));

        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        assertThat(solver.getSolution(csp)).isEmpty();
    }

    @Test
    void nogoodsAreRecordedDuringSearch() {
        // x∈{1}, y∈{1}, x≠y: assigning x=1 passes isConsistent (y is unassigned)
        // but MAC then wipes out y's domain (removes 1 via x≠y) → inference failure
        // → one nogood is recorded covering the current assignment.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        NogoodStore store = new NogoodStore();
        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        solver.getSolutions(csp).toList();
        assertThat(store.size()).isGreaterThan(0);
    }

    @Test
    void nogoodsLearnedStatisticIncrementsOnFailedBranch() {
        // x ∈ {1,2} is weighted (via two extra constraints against singleton dummies w1,w2) so
        // dom/wdeg selects x before the singleton y ∈ {1}. With ascending value order, x=1 is
        // tried first — it passes isConsistent but MAC wipes out y's domain (only value 1 removed
        // by x≠y) → nogood recorded, backtrack to x=2 → solution (2,1,5,6). The failed x=1 branch
        // and the successful x=2 branch share the same root Statistics.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        Variable<Integer> w1 = VF.create("w1");
        Variable<Integer> w2 = VF.create("w2");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .variableDomain(w1, IntRangeDomain.of(5, 5))
                .variableDomain(w2, IntRangeDomain.of(6, 6))
                .notEqualsConstraint(x, y)
                .notEqualsConstraint(x, w1)
                .notEqualsConstraint(x, w2)
                .build();

        NogoodStore store = new NogoodStore();
        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        List<Assignment> solutions = solver.getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(x).orElseThrow()).isEqualTo(2);
        assertThat(solutions.get(0).getStatistics().getNogoodsLearned().get()).isGreaterThan(0);
    }

    @Test
    void getSolutionUsesInferenceProvidedReasonNotRawAssignment() {
        // Same setup as nogoodsAreRecordedDuringSearch, but via getSolution() (the Luby-restart
        // path), and with a custom Inference (decorating FULL_PROPAGATION_INFERENCE) whose
        // applyWithReason always reports the sentinel {x=99} instead of whatever the real failure
        // would derive. If inferOrExplain recorded next.getValues() directly rather than trusting
        // inference's own reason, the store would hold a nogood over {x=1}, not {x=99}.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        Inference sentinelInference = new Inference() {
            @Override
            public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem c, Variable<?> variable, Assignment assignment) {
                return Solver.Factory.FULL_PROPAGATION_INFERENCE.apply(c, variable, assignment);
            }

            @Override
            public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem c, Variable<?> variable, Assignment assignment) {
                ConsistencyResult result = Solver.Factory.FULL_PROPAGATION_INFERENCE.applyWithReason(c, variable, assignment);
                return result.isInfeasible()
                        ? ConsistencyResult.infeasible(GroundNogoodConstraint.of(java.util.Map.of(x, 99)))
                        : result;
            }
        };

        NogoodStore store = new NogoodStore();
        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(sentinelInference)
                .nogoodStore(store)
                .build();

        assertThat(solver.getSolution(csp)).isEmpty();
        var constraints = store.apply(csp).getConstraints();
        assertThat(constraints).contains(GroundNogoodConstraint.of(java.util.Map.of(x, 99)));
        assertThat(constraints).doesNotContain(GroundNogoodConstraint.of(java.util.Map.of(x, 1)));
    }

    @Test
    void nogoodExplanationQualityDependsOnWhetherImpossibleVariableIsAlreadySingleton() {
        // Two deliberately conflicting cardinality constraints over {a,b,c} (each in {1,2,3,4}):
        // "exactly 1 of {a,b,c} equals 1" and "exactly 1 of {b,c} equals 2". Deciding a=2 (excluded
        // from the first target) leaves b,c genuinely open; deciding b=3 (excluded from both
        // targets) forces c=1 via the first constraint (the only remaining candidate for target 1)
        // -- but c=1 excludes it from target 2 too, so the second constraint's own propagation (in
        // the SAME fixpoint pass) then finds no candidate left for target 2 and fails. Crucially,
        // c is NEVER explicitly decided by search here -- it's forced purely by propagation.
        //
        // Both w=10 and w=20 hit this same conflict shape (for b=3 and b=4), giving 4 opportunities
        // to learn a nogood, but only the first two calls to reach it produce the general, reusable
        // reason {b:3, c:1} / {b:4, c:1}. Whichever subtree runs second inherits those two already
        // -- their propagation prunes value 1 from c's domain before CountConstraint ever runs, so
        // c becomes "impossible" (can't be 1) but is left with {2,3,4}, not singleton.
        // CountConstraint#explainInfeasible can only cite an impossible variable when it's singleton
        // (Propagatable#allSingletonReason): the nogood format is equality-only ("variable = value"),
        // with no way to express "variable != value" as a literal, so a non-singleton impossible
        // variable simply can't be cited soundly. That branch's explanation falls back to the full
        // assignment ({w:10, a:2, b:3} etc.) -- still sound, just not general enough to be reused,
        // so the second subtree independently records its own (looser) pair. Net result: 4 distinct,
        // individually sound nogoods, not 2 -- reusing an earlier general nogood's partial pruning
        // can ironically make a later branch's own explanation less precise, not more.
        Variable<Integer> w  = VF.create("nw");
        Variable<Integer> a  = VF.create("na");
        Variable<Integer> b  = VF.create("nb");
        Variable<Integer> c  = VF.create("nc");
        Variable<Integer> d1 = VF.create("nd1");
        Variable<Integer> d2 = VF.create("nd2");
        Variable<Integer> d3 = VF.create("nd3");
        // w needs a smaller domainSize/weightedDegree ratio than a (2/1=2) so dom/wdeg picks it
        // first, creating two separate subtrees; three dummy notEquals constraints against
        // singleton decoys give w weighted degree 3, ratio 2/3.
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(w, new IntRangeDomain(Set.of(10, 20)))
                .variableDomain(a, IntRangeDomain.of(1, 2))
                .variableDomain(b, IntRangeDomain.of(1, 4))
                .variableDomain(c, IntRangeDomain.of(1, 4))
                .variableDomain(d1, IntRangeDomain.of(99, 99))
                .variableDomain(d2, IntRangeDomain.of(99, 99))
                .variableDomain(d3, IntRangeDomain.of(99, 99))
                .notEqualsConstraint(w, d1)
                .notEqualsConstraint(w, d2)
                .notEqualsConstraint(w, d3)
                .countConstraint(Set.of(a, b, c), 1, io.github.rcrida.jcsp.constraints.Operator.EQ, 1)
                .countConstraint(Set.of(b, c), 2, io.github.rcrida.jcsp.constraints.Operator.EQ, 1)
                .build();

        NogoodStore store = new NogoodStore();
        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        List<Assignment> solutions = solver.getSolutions(csp).toList();
        assertThat(solutions).hasSize(12); // 2 (w) x 6 (valid a,b,c combos)
        assertThat(store.size()).isEqualTo(4);
    }

    // ── Builder validation ────────────────────────────────────────────────────

    @Test
    void builderRejectsNonPositiveLubyUnit() {
        assertThatThrownBy(() -> DomWdegLubySearch.builder()
                .lubyUnit(0)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lubyUnit");
    }

    @Test
    void builderRejectsNonPositiveMaxRestarts() {
        assertThatThrownBy(() -> DomWdegLubySearch.builder()
                .maxRestarts(0)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRestarts");
    }

    // ── Limits ────────────────────────────────────────────────────────────────

    private static ConstraintSatisfactionProblem fourQueensCsp() {
        int n = 4;
        @SuppressWarnings("unchecked")
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

    @Test
    void nodeLimitStopsGetSolution() {
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofNodes(1))
                .build();

        assertThatThrownBy(() -> limited.getSolution(fourQueensCsp()))
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .isNotNull();
    }

    @Test
    void timeLimitStopsGetSolution() {
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofTime(Duration.ofNanos(1)))
                .build();

        assertThatThrownBy(() -> limited.getSolution(fourQueensCsp()))
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .isNotNull();
    }

    @Test
    void nodeLimitStopsGetSolutions() {
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofNodes(1))
                .build();

        assertThat(limited.getSolutions(fourQueensCsp()).findFirst()).isEmpty();
    }

    @Test
    void timeLimitStopsGetSolutions() {
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofTime(Duration.ofNanos(1)))
                .build();

        assertThat(limited.getSolutions(fourQueensCsp()).findFirst()).isEmpty();
    }

    @Test
    void limitExceededStatisticsIncludeBacktracksAndNogoodsLearned() {
        // Same x/y/w1/w2 CSP as nogoodsLearnedStatisticIncrementsOnFailedBranch: x=1 fails (a
        // nogood is recorded and the branch backtracks), x=2 succeeds. A node limit of 2 lets that
        // failed x=1 branch fully resolve -- recording the backtrack and the nogood -- before the
        // limit fires on the very next node (the x=2 attempt). Before the fix, searchOne's
        // limit-exceeded path reported a fresh, zeroed Statistics here (backtracks=0,
        // nogoodsLearned=0) despite this real activity already having happened; it should now
        // reuse the real accumulated Statistics instead.
        Variable<Integer> x = VF.create("x");
        Variable<Integer> y = VF.create("y");
        Variable<Integer> w1 = VF.create("w1");
        Variable<Integer> w2 = VF.create("w2");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .variableDomain(w1, IntRangeDomain.of(5, 5))
                .variableDomain(w2, IntRangeDomain.of(6, 6))
                .notEqualsConstraint(x, y)
                .notEqualsConstraint(x, w1)
                .notEqualsConstraint(x, w2)
                .build();

        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .domainValuesOrderer(io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofNodes(2))
                .build();

        assertThatThrownBy(() -> limited.getSolution(csp))
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .satisfies(rawStats -> {
                    Statistics stats = (Statistics) rawStats;
                    assertThat(stats.getNodesExplored().get()).isEqualTo(2);
                    assertThat(stats.getBacktracks().get()).isGreaterThan(0);
                    assertThat(stats.getNogoodsLearned().get()).isGreaterThan(0);
                });
    }

    @Test
    void nodeLimitAccumulatesAcrossLubyRestarts() {
        // lubyUnit=1 forces many restarts (budget sequence 1,1,2,...).
        // With nodeLimit=2, the cumulative count across restarts hits the limit
        // after at most 2 node assignments total, so 4-queens (needs many more) throws.
        DomWdegLubySearch limited = DomWdegLubySearch.builder()
                .lubyUnit(1)
                .maxRestarts(512)
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(SolverLimits.ofNodes(2))
                .build();

        assertThatThrownBy(() -> limited.getSolution(fourQueensCsp()))
                .isInstanceOf(LimitExceededException.class)
                .extracting(e -> ((LimitExceededException) e).getStatistics())
                .isNotNull();
    }
}
