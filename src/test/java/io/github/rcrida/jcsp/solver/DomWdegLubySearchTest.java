package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
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
        store.record(java.util.Map.of(x, 1));

        DomWdegLubySearch solver = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .nogoodStore(store)
                .build();

        List<Assignment> solutions = solver.getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(x).orElseThrow()).isEqualTo(2);
        assertThat(solutions.get(0).getStatistics().getNogoodPrunes().get()).isGreaterThan(0);
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
        store.record(java.util.Map.of(x, 1));
        store.record(java.util.Map.of(x, 2));

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
    void getSolutionUsesConflictExplainerNotRawAssignment() {
        // Same setup as nogoodsAreRecordedDuringSearch, but via getSolution() (the Luby-restart
        // path). A custom conflictExplainer always reports the sentinel {x=99} instead of the
        // real assigned value. If searchOne recorded next.getValues() directly (the pre-fix
        // behaviour) the store would hold {x=1}, not {x=99}.
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
                .conflictExplainer((c, variable, assignment) -> java.util.Map.of(x, 99))
                .build();

        assertThat(solver.getSolution(csp)).isEmpty();
        assertThat(store.isViolated(Assignment.of(java.util.Map.of(x, 99)))).isTrue();
        assertThat(store.isViolated(Assignment.of(java.util.Map.of(x, 1)))).isFalse();
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
