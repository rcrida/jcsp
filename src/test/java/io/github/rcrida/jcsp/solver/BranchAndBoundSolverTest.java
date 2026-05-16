package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.backtrackingsearch.BacktrackingSearch;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class BranchAndBoundSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final BacktrackingSearch BACKTRACKING = new BacktrackingSearch(
            MinimumRemainingValuesSelector.INSTANCE,
            DefaultValueOrderer.INSTANCE,
            (problem, variable, assignment) -> Optional.of(problem));

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

    static int sum(io.github.rcrida.jcsp.assignments.Assignment a) {
        return a.getValue(X).orElse(0) + a.getValue(Y).orElse(0) + a.getValue(Z).orElse(0);
    }

    @Test
    void optimize_findsMinimumSum() {
        val solver = BranchAndBoundSolver.builder().inner(BACKTRACKING).build();
        val result = solver.getSolution(CSP, a -> sum(a));
        assertThat(result).isPresent();
        assertThat(sum(result.get())).isEqualTo(6);
    }

    @Test
    void getSolutions_returnsImprovingStream() {
        val solver = BranchAndBoundSolver.builder().inner(BACKTRACKING).build();
        val improving = solver.getSolutions(CSP, a -> sum(a)).toList();
        assertThat(improving).isNotEmpty();
        // Each solution is strictly better than the previous
        for (int i = 1; i < improving.size(); i++) {
            assertThat(sum(improving.get(i))).isLessThan(sum(improving.get(i - 1)));
        }
        // Last is the global optimum
        assertThat(sum(improving.getLast())).isEqualTo(6);
    }

    @Test
    void defaultSolverInterface_alsoGetsOptimization() {
        Solver solver = BranchAndBoundSolver.builder().inner(BACKTRACKING).build();
        val result = solver.getSolution(CSP, a -> sum(a));
        assertThat(result).isPresent();
        assertThat(sum(result.get())).isEqualTo(6);
    }

    @Test
    void getSolutions_noObjective_delegatesToFallback() {
        val solver = BranchAndBoundSolver.builder().inner(BACKTRACKING).build();
        // Without objective: all 60 allDiff assignments of {1..5} choose 3
        assertThat(solver.getSolutions(CSP)).hasSize(60);
    }

    @Test
    void earlyTermination_returnsApproximateSolution() {
        val solver = BranchAndBoundSolver.builder().inner(BACKTRACKING).build();
        val first = solver.getSolutions(CSP, a -> sum(a)).findFirst();
        assertThat(first).isPresent();
        assertThat(sum(first.get())).isLessThanOrEqualTo(12); // anything valid, not necessarily optimal
    }

    @Test
    void defaultSolver_getSolutions_withObjective_returnsImprovingStream() {
        // Exercises the default getSolutions(csp, objective) on a non-overriding Solver
        Solver solver = Solver.Factory.INSTANCE.createSolver();
        val improving = solver.getSolutions(CSP, a -> sum(a)).toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(sum(improving.get(i))).isLessThan(sum(improving.get(i - 1)));
        }
        assertThat(sum(improving.getLast())).isEqualTo(6);
    }

    @Test
    void defaultSolver_getSolution_withObjective_findsOptimum() {
        // Single variable, no constraints; objective decreases with increasing x so the solver
        // (which explores domain values in order) produces 5 improving solutions. This exercises
        // the reduce lambda inside the default getSolution(csp, objective).
        Variable<Integer> v = F.create("v");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v, IntRangeDomain.of(1, 5))
                .build();
        Solver solver = Solver.Factory.INSTANCE.createSolver();
        // cost(1)=50, cost(2)=40, ..., cost(5)=10  → optimal is v=5
        val result = solver.getSolution(csp, a -> 60 - 10 * a.getValue(v).orElse(0));
        assertThat(result).isPresent();
        assertThat(result.get().getValue(v)).contains(5);
    }
}
