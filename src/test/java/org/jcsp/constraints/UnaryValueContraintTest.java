package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnaryValueContraintTest {
    @Mock
    Variable variable;
    @Mock
    Object value;
    @Mock
    Object anotherValue;

    @Test
    void constructValid() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        assertDoesNotThrow(() -> new UnaryValueConstraint(variable, value));
    }

    @Test
    void constructInvalid() {
        when(variable.isAllowedValue(value)).thenReturn(false);
        assertThatThrownBy(() -> new UnaryValueConstraint(variable, value))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Invalid constraint value for variable 'variable': value");
    }

    @Test
    void isSatisfied() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var constraint = new UnaryValueConstraint(variable, value);
        var assignment = new Assignment(Map.of(variable, value));
        assertThat(constraint.isSatisfied(assignment)).isTrue();
    }

    @Test
    void isSatisfiedAnotherValue() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        when(variable.isAllowedValue(anotherValue)).thenReturn(true);
        var constraint = new UnaryValueConstraint(variable, value);
        var assignment = new Assignment(Map.of(variable, anotherValue));
        assertThat(constraint.isSatisfied(assignment)).isFalse();
    }

    @Test
    void isSatisfiedUnassigned() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var constraint = new UnaryValueConstraint(variable, value);
        var assignment = new Assignment(Map.of());
        assertThat(constraint.isSatisfied(assignment)).isFalse();
    }
}
