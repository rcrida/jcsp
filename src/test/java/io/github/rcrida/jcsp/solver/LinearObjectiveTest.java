package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class LinearObjectiveTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    Variable<Integer> x = F.create("x");
    Variable<Integer> y = F.create("y");

    @Test
    void fullAssignment_sumsConstantAndWeightedTerms() {
        LinearObjective objective = LinearObjective.builder()
                .coefficient(x, 2.0)
                .coefficient(y, 3.0)
                .constant(1.0)
                .build();
        double value = objective.applyAsDouble(Assignment.of(Map.of(x, 4, y, 5)));
        assertThat(value).isCloseTo(1.0 + 2.0 * 4 + 3.0 * 5, within(1e-9));
    }

    @Test
    void partialAssignment_missingVariableContributesZero() {
        LinearObjective objective = LinearObjective.builder()
                .coefficient(x, 2.0)
                .coefficient(y, 3.0)
                .constant(1.0)
                .build();
        double value = objective.applyAsDouble(Assignment.of(Map.of(x, 4)));
        assertThat(value).isCloseTo(1.0 + 2.0 * 4, within(1e-9));
    }

    @Test
    void defaultConstant_isZero() {
        LinearObjective objective = LinearObjective.builder().coefficient(x, 1.0).build();
        assertThat(objective.applyAsDouble(Assignment.of(Map.of(x, 7)))).isCloseTo(7.0, within(1e-9));
    }

    @Test
    void implementsToDoubleFunction_usableDirectlyAsAnObjective() {
        LinearObjective objective = LinearObjective.builder().coefficient(x, 5.0).build();
        ToDoubleFunction<Assignment> asFunction = objective;
        assertThat(asFunction.applyAsDouble(Assignment.of(Map.of(x, 2)))).isCloseTo(10.0, within(1e-9));
    }
}
