package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
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
public class BinaryExpressionConstraintTest {
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Function<Assignment, Boolean> expression;
    BinaryExpressionConstraint expressionConstraint;

    @BeforeEach
    void setUp() {
        expressionConstraint = new BinaryExpressionConstraint(variable1, variable2, expression);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfied(boolean satisfied) {
        var assignment = new Assignment(Map.of());
        when(expression.apply(assignment)).thenReturn(satisfied);
        assertThat(expressionConstraint.isSatisfied(assignment)).isEqualTo(satisfied);
    }
}
