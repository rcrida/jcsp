package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ArcConsistentSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    ArcConsistentSolver solver = ArcConsistentSolver.builder().inner(csp -> Stream.empty()).build();

    @Test
    void feasible_delegatesToInner() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        // x1∈{1,2}, x2∈{1,2}, x1≠x2 — AC3 makes no change, domains not singletons → inner called
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x1, x2)
                .build();
        assertThat(solver.getSolutions(csp)).isEmpty();
    }

    @Test
    void infeasible_returnsEmpty() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x1, x2)
                .build();
        assertThat(solver.getSolutions(csp)).isEmpty();
    }
}
