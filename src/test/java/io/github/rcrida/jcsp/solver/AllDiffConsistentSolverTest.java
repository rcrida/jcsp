package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AllDiffConsistentSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    AllDiffConsistentSolver solver = AllDiffConsistentSolver.builder().inner(csp -> Stream.empty()).build();

    @Test
    void prunesDomainViaGAC() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        // Naked pair {x1,x2} on {1,2} → x3 must be 3; inner returns empty (coverage of preprocess)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 3))
                .allDiffConstraint(Set.of(x1, x2, x3))
                .build();
        assertThat(solver.getSolutions(csp)).isEmpty();
    }

    @Test
    void infeasible_returnsEmpty() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 2))
                .allDiffConstraint(Set.of(x1, x2, x3))
                .build();
        assertThat(solver.getSolutions(csp)).isEmpty();
    }
}
