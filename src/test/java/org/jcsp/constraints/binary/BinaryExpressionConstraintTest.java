package org.jcsp.constraints.binary;

import lombok.val;
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
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BinaryExpressionConstraintTest {
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    BiFunction<Object, Object, Boolean> expression;
    @Mock
    BiFunction<Object, Object, Boolean> anotherExpression;
    BinaryExpressionConstraint binaryExpressionConstraint;

    @BeforeEach
    void setUp() {
        binaryExpressionConstraint = BinaryExpressionConstraint.builder().left(variable1).right(variable2).expression(expression).build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy(boolean satisfied) {
        var assignment = Assignment.of(Map.of(variable1, value1, variable2, value2));
        when(expression.apply(value1, value2)).thenReturn(satisfied);
        assertThat(binaryExpressionConstraint.isSatisfiedBy(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(binaryExpressionConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1)))).isTrue();
        assertThat(binaryExpressionConstraint.isSatisfiedBy(Assignment.of(Map.of(variable2, value2)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(binaryExpressionConstraint.toString()).isEqualTo("<(variable1, variable2), expression>");
    }

    @Test
    void equals() {
        val anotherBinaryExpressionConstraint = BinaryExpressionConstraint.builder().left(variable1).right(variable2).expression(anotherExpression).build();
        assertThat(binaryExpressionConstraint).isNotEqualTo(anotherBinaryExpressionConstraint);
        val sameBinaryExpressionConstraint = BinaryExpressionConstraint.builder().left(variable1).right(variable2).expression(expression).build();
        assertThat(binaryExpressionConstraint).isEqualTo(sameBinaryExpressionConstraint);
    }
}
