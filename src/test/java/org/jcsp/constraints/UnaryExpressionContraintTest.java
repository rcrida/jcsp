package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnaryExpressionContraintTest {
    @Mock
    Variable variable;
    @Mock
    Object value;
    @Mock
    Function<Object, Boolean> expression;
    UnaryExpressionConstraint unaryExpressionConstraint;

    @BeforeEach
    void setUp() {
        unaryExpressionConstraint = new UnaryExpressionConstraint(variable, expression);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfied(boolean satisfied) {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        when(expression.apply(value)).thenReturn(satisfied);
        assertThat(unaryExpressionConstraint.isSatisfied(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedUnassigned() {
        var assignment = new Assignment(Map.of());
        assertThat(unaryExpressionConstraint.isSatisfied(assignment)).isFalse();
    }
}
