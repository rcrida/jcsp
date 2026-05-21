package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x");
    static final Variable<Integer> Y = F.create("y");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 5))
            .variableDomain(Y, IntRangeDomain.of(1, 5))
            .allDiffConstraint(Set.of(X, Y))
            .build();

    @Test
    void defaultGetLocalSolution_withObjective_delegatesToSatisfactionSearch() {
        // A LocalSolver that does not override the objective overload: the default must
        // delegate to getLocalSolution(csp) and ignore the objective.
        LocalSolver solver = csp -> {
            val builder = io.github.rcrida.jcsp.assignments.Assignment.builder();
            builder.value(X, 1).value(Y, 2);
            return Optional.of(builder.build());
        };

        val result = solver.getLocalSolution(CSP, a -> a.getValue(X).orElse(0) + a.getValue(Y).orElse(0));

        assertThat(result).isPresent();
    }
}
