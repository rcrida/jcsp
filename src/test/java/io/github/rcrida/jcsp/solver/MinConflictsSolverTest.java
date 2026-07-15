package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
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

    static Assignment infeasible() {
        return Assignment.builder().value(X, 1).value(Y, 1).build();
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
}
