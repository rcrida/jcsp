package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.variables.VariablesByName;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AtLeastNConstraintWithCountingTest {
    /** Four booleans named {@code v1..v4}, issued by {@code builder}. */
    static Set<Variable<Boolean>> fourBooleans(ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder) {
        Set<Variable<Boolean>> variables = new LinkedHashSet<>();
        for (int i = 1; i <= 4; i++) variables.add(builder.createVariable("v" + i, BooleanDomain.INSTANCE));
        return variables;
    }

    /** The four booleans {@code csp} was built over, as distinct from any auxiliary variable it also holds. */
    static Set<Variable<Boolean>> fourBooleans(ConstraintSatisfactionProblem csp) {
        Set<Variable<Boolean>> variables = new LinkedHashSet<>();
        for (int i = 1; i <= 4; i++) variables.add(VariablesByName.in(csp, "v" + i));
        return variables;
    }

    static ConstraintSatisfactionProblem csp(int n) {
        val builder = ConstraintSatisfactionProblem.builder();
        return builder.atLeastNConstraintWithCounting(fourBooleans(builder), n).build();
    }

    @Test
    void solutionCountMatchesAtLeastNConstraint() {
        // Compare solution counts for each n against the plain atLeastNConstraint
        for (int n = 0; n <= 4; n++) {
            val plainBuilder = ConstraintSatisfactionProblem.builder();
            val plain = plainBuilder.atLeastNConstraint(fourBooleans(plainBuilder), n).build();

            val cspN = csp(n);
            long countingCount = Solver.Factory.INSTANCE.createSolver(cspN).getSolutions().count();
            long plainCount = Solver.Factory.INSTANCE.createSolver(plain).getSolutions().count();

            assertThat(countingCount)
                    .as("atLeast%d: counting chain vs plain", n)
                    .isEqualTo(plainCount);
        }
    }

    @Test
    void atLeast2_onlySolutionsWithTwoOrMoreTrue() {
        val csp = csp(2);
        val inputs = fourBooleans(csp);
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).allSatisfy(sol -> {
            long trueCount = inputs.stream()
                    .filter(v -> sol.getValue(v).orElse(false))
                    .count();
            assertThat(trueCount).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void atLeast4_onlyAllTrueSolution() {
        val csp = csp(4);
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        for (Variable<Boolean> input : fourBooleans(csp)) {
            assertThat(solutions.get(0).getValue(input)).hasValue(true);
        }
    }
}
