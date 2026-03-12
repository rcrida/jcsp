package org.jcsp.assignments;

import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AssignmentTest {
    @Mock
    Variable variable;
    @Mock
    Object value;
    @Mock
    Variable anotherVariable;
    @Mock
    Object anotherValue;

    @Test
    void constructValid() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        assertDoesNotThrow(() -> new Assignment(Map.of(variable, value)));
    }

    @Test
    void constructInvalid() {
        when(variable.isAllowedValue(value)).thenReturn(false);
        assertThatThrownBy(() -> new Assignment(Map.of(variable, value)))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Invalid assigned value for variable 'variable': value");
    }

    @Test
    void getValueKnown() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        assertThat(assignment.getValue(variable)).contains(value);
    }

    @Test
    void getValueUnknown() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        assertThat(assignment.getValue(anotherVariable)).isEmpty();
    }
    @Test
    void extractPartialAssignment() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        when(anotherVariable.isAllowedValue(anotherValue)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value, anotherVariable, anotherValue));
        var partialAssignment = assignment.extractPartialAssignment(Set.of(variable));
        assertThat(partialAssignment.getValues()).isEqualTo(Map.of(variable, value));
    }
}
