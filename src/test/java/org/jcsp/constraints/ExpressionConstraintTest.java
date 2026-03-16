package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExpressionConstraintTest {
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    Domain domain;
    @Mock
    Function<Assignment, Boolean> expression;
    ExpressionConstraint expressionConstraint;

    @BeforeEach
    void setUp() {
        expressionConstraint = ExpressionConstraint.builder().variables(Set.of(variable1, variable2)).expression(expression).build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy(boolean satisfied) {
        when(domain.contains(value1)).thenReturn(true);
        when(domain.contains(value2)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable2.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        when(variable2.isAllowedValue(value2)).thenCallRealMethod();
        var assignment = new Assignment(Map.of(variable1, value1, variable2, value2));
        when(expression.apply(assignment)).thenReturn(satisfied);
        assertThat(expressionConstraint.isSatisfiedBy(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(expressionConstraint.isSatisfiedBy(new Assignment(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(expressionConstraint.toString()).isEqualTo("<(variable1, variable2), expression>");
    }
}
