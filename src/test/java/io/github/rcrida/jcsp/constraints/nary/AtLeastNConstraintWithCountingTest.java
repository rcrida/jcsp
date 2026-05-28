package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AtLeastNConstraintWithCountingTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Boolean> V1 = F.create("v1");
    static final Variable<Boolean> V2 = F.create("v2");
    static final Variable<Boolean> V3 = F.create("v3");
    static final Variable<Boolean> V4 = F.create("v4");

    static ConstraintSatisfactionProblem csp(int n) {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(V1, BooleanDomain.INSTANCE)
                .variableDomain(V2, BooleanDomain.INSTANCE)
                .variableDomain(V3, BooleanDomain.INSTANCE)
                .variableDomain(V4, BooleanDomain.INSTANCE)
                .atLeastNConstraintWithCounting(Set.of(V1, V2, V3, V4), n)
                .build();
    }

    @Test
    void solutionCountMatchesAtLeastNConstraint() {
        // Compare solution counts for each n against the plain atLeastNConstraint
        for (int n = 0; n <= 4; n++) {
            val plain = ConstraintSatisfactionProblem.builder()
                    .variableDomain(V1, BooleanDomain.INSTANCE)
                    .variableDomain(V2, BooleanDomain.INSTANCE)
                    .variableDomain(V3, BooleanDomain.INSTANCE)
                    .variableDomain(V4, BooleanDomain.INSTANCE)
                    .atLeastNConstraint(Set.of(V1, V2, V3, V4), n)
                    .build();

            long countingCount = Solver.Factory.INSTANCE.createSolver()
                    .getSolutions(csp(n)).count();
            long plainCount = Solver.Factory.INSTANCE.createSolver()
                    .getSolutions(plain).count();

            assertThat(countingCount)
                    .as("atLeast%d: counting chain vs plain", n)
                    .isEqualTo(plainCount);
        }
    }

    @Test
    void atLeast2_onlySolutionsWithTwoOrMoreTrue() {
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp(2)).toList();
        assertThat(solutions).allSatisfy(sol -> {
            long trueCount = Set.of(V1, V2, V3, V4).stream()
                    .filter(v -> sol.getValue(v).orElse(false))
                    .count();
            assertThat(trueCount).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void atLeast4_onlyAllTrueSolution() {
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp(4)).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(V1)).hasValue(true);
        assertThat(solutions.get(0).getValue(V2)).hasValue(true);
        assertThat(solutions.get(0).getValue(V3)).hasValue(true);
        assertThat(solutions.get(0).getValue(V4)).hasValue(true);
    }
}
