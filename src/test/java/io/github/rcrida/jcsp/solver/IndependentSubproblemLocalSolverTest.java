package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.GreedyAssignmentFactory;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class IndependentSubproblemLocalSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> V1 = F.create("V1");
    static final Variable<Integer> V2 = F.create("V2");
    static final Variable<Integer> V3 = F.create("V3");
    static final Variable<Integer> V4 = F.create("V4");

    // Two independent subproblems: {V1 != V2} over {1,2,3} and {V3 != V4} over {1,2}
    static final ConstraintSatisfactionProblem TWO_SUBPROBLEM_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(V1, IntRangeDomain.of(1, 3))
            .variableDomain(V2, IntRangeDomain.of(1, 3))
            .variableDomain(V3, IntRangeDomain.of(1, 2))
            .variableDomain(V4, IntRangeDomain.of(1, 2))
            .notEqualsConstraint(V1, V2)
            .notEqualsConstraint(V3, V4)
            .build();

    static final ConstraintSatisfactionProblem SINGLE_SUBPROBLEM_CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(V1, IntRangeDomain.of(1, 3))
            .variableDomain(V2, IntRangeDomain.of(1, 3))
            .notEqualsConstraint(V1, V2)
            .build();

    IndependentSubproblemLocalSolver solver = IndependentSubproblemLocalSolver.builder()
            .delegate(MinConflictsSolver.of(1, 500, GreedyAssignmentFactory.INSTANCE))
            .build();

    @Test
    void findsSolutionAcrossIndependentSubproblems() {
        val solution = solver.getLocalSolution(TWO_SUBPROBLEM_CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(TWO_SUBPROBLEM_CSP)).isTrue();
        assertThat(solution.get().getValue(V1)).isNotEqualTo(solution.get().getValue(V2));
        assertThat(solution.get().getValue(V3)).isNotEqualTo(solution.get().getValue(V4));
    }

    @Test
    void findsSolutionForSingleSubproblem() {
        val solution = solver.getLocalSolution(SINGLE_SUBPROBLEM_CSP);
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(SINGLE_SUBPROBLEM_CSP)).isTrue();
    }

    @Test
    void withObjective_findsSolutionAcrossIndependentSubproblems() {
        val solution = solver.getLocalSolution(TWO_SUBPROBLEM_CSP,
                a -> a.getValue(V1).orElse(Integer.MAX_VALUE).doubleValue());
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(TWO_SUBPROBLEM_CSP)).isTrue();
    }

    @Test
    void returnsEmptyWhenAnySubproblemUnsolvable() {
        val infeasible = ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, IntRangeDomain.of(1, 1))
                .variableDomain(V2, IntRangeDomain.of(1, 1))
                .variableDomain(V3, IntRangeDomain.of(1, 2))
                .variableDomain(V4, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(V1, V2) // unsatisfiable: V1 and V2 both forced to 1
                .notEqualsConstraint(V3, V4)
                .build();
        val infeasibleSolver = IndependentSubproblemLocalSolver.builder()
                .delegate(MinConflictsSolver.of(1, 0, GreedyAssignmentFactory.INSTANCE))
                .build();
        assertThat(infeasibleSolver.getLocalSolution(infeasible)).isEmpty();
    }
}
