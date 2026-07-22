package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MinConflictsSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");

    // X ∈ {1,2,3}, Y ∈ {1,2,3}, allDiff — six solutions exist
    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 3))
            .variableDomain(Y, IntRangeDomain.of(1, 3))
            .allDiffConstraint(Set.of(X, Y))
            .build();

    static final Set<String> SET_UNIVERSE = Set.of("p", "q", "r", "s", "t", "u");
    static final Variable<Set<String>> A = F.create("a");
    static final Variable<Set<String>> B = F.create("b");

    // A, B ⊆ {p..u}, |A|,|B| ∈ [2,3], disjoint — plenty of room to repair via add/remove/swap.
    static final ConstraintSatisfactionProblem DISJOINT_SET_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(A, SetIntervalDomain.of(Set.of(), SET_UNIVERSE, 2, 3))
            .variableDomain(B, SetIntervalDomain.of(Set.of(), SET_UNIVERSE, 2, 3))
            .disjointConstraint(A, B)
            .build();

    // A is pinned to a singleton by its own domain (no legal move for it); only B can repair the
    // overlap, but ConflictedVariableSelector may still pick A at some step since it's touched by
    // the violated constraint — SetDomainMoves.neighbours must handle that ("no legal moves") case.
    static final ConstraintSatisfactionProblem DISJOINT_SET_CSP_WITH_SINGLETON_A = ConstraintSatisfactionProblem.builder()
            .variableDomain(A, SetIntervalDomain.of(Set.of("p", "q"), Set.of("p", "q"), 2, 2))
            .variableDomain(B, SetIntervalDomain.of(Set.of(), SET_UNIVERSE, 2, 3))
            .disjointConstraint(A, B)
            .build();

    static Assignment infeasible() {
        return Assignment.builder().value(X, 1).value(Y, 1).build();
    }

    static Assignment overlappingSets() {
        return Assignment.builder().value(A, Set.of("p", "q")).value(B, Set.of("p", "r")).build();
    }

    @Test
    void getLocalSolution_findsSolution() {
        val solver = MinConflictsSolver.of(1, 500, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP)).isPresent();
    }

    @Test
    void getLocalSolution_returnsEmptyWhenMaxStepsExhausted() {
        val solver = MinConflictsSolver.of(1, 0, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP)).isEmpty();
    }

    @Test
    void getLocalSolution_withObjective_returnsEmptyWhenMaxStepsExhausted() {
        val solver = MinConflictsSolver.of(1, 0, csp -> infeasible());
        assertThat(solver.getLocalSolution(CSP, a -> 0.0)).isEmpty();
    }

    @Test
    void getLocalSolution_withObjective_returnsLowestCostSolution() {
        // Objective is X value — optimal solution has X=1.
        // With maxRestarts=29 (30 attempts), the probability of never selecting the optimal
        // repair path (which occurs ~50% of the time per attempt) is (0.5)^30 ≈ 10^-9.
        val solver = MinConflictsSolver.of(30, 50, csp -> infeasible());
        Optional<Assignment> result = solver.getLocalSolution(CSP,
                a -> a.getValue(X).orElse(Integer.MAX_VALUE).doubleValue());
        assertThat(result).isPresent();
        assertThat(result.get().getValue(X)).hasValue(1);
    }

    @Test
    void getLocalSolution_withObjective_doesNotUpdateBestWhenCostNotImproving() {
        // Constant objective means every feasible solution has the same cost.
        // After the first restart records best, subsequent restarts hit the cost >= bestCost branch.
        val solver = MinConflictsSolver.of(1, 500, csp -> infeasible());
        Optional<Assignment> result = solver.getLocalSolution(CSP, a -> 1.0);
        assertThat(result).isPresent();
    }

    @Test
    void withCancellation_stopsSearchBeforeTheFirstStep() {
        val cancellation = new Cancellation();
        cancellation.cancel();
        val solver = MinConflictsSolver.of(1, 500, csp -> infeasible()).withCancellation(cancellation);
        assertThat(solver.getLocalSolution(CSP)).isEmpty();
    }

    @Test
    void withCancellation_withObjective_stopsSearchBeforeTheFirstStep() {
        val cancellation = new Cancellation();
        cancellation.cancel();
        val solver = MinConflictsSolver.of(1, 500, csp -> infeasible()).withCancellation(cancellation);
        assertThat(solver.getLocalSolution(CSP, a -> 0.0)).isEmpty();
    }

    @Test
    void getLocalSolution_setBoundedDomain_repairsOverlapToDisjointSets() {
        val solver = MinConflictsSolver.of(5, 200, csp -> overlappingSets());
        val solution = solver.getLocalSolution(DISJOINT_SET_CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(DISJOINT_SET_CSP)).isTrue();
    }

    @Test
    void getLocalSolution_setBoundedDomain_withObjective_repairsOverlapToDisjointSets() {
        val solver = MinConflictsSolver.of(5, 200, csp -> overlappingSets());
        val solution = solver.getLocalSolution(DISJOINT_SET_CSP, a -> 0.0);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(DISJOINT_SET_CSP)).isTrue();
    }

    @Test
    void getLocalSolution_setBoundedDomain_singletonParticipantDoesNotCrash() {
        // Over 200 steps with only two variables touching the violated constraint, A (the
        // singleton) is selected by ConflictedVariableSelector roughly half the time -- exercising
        // SetDomainMoves.neighbours' "no legal move" path for real, not just hypothetically.
        val solver = MinConflictsSolver.of(5, 200, csp -> overlappingSets());
        val solution = solver.getLocalSolution(DISJOINT_SET_CSP_WITH_SINGLETON_A);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(DISJOINT_SET_CSP_WITH_SINGLETON_A)).isTrue();
        assertThat(solution.get().getValue(A)).hasValue(Set.of("p", "q"));
    }
}
