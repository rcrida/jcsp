package org.jcsp.assignments;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.unary.UnaryNotEqualsConstraint;
import org.jcsp.constraints.unary.UnaryValueConstraint;
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
    void empty() {
        assertThat(Assignment.EMPTY.getValues()).isEmpty();
    }

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

    @Test
    void withValue() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        when(variable.isAllowedValue(anotherValue)).thenReturn(true);
        assertThat(assignment.withValue(variable, anotherValue).getValue(variable)).contains(anotherValue);
    }

    @Test
    void merge() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment1 = new Assignment(Map.of(variable, value));
        when(anotherVariable.isAllowedValue(anotherValue)).thenReturn(true);
        var assignment2 = new Assignment(Map.of(anotherVariable, anotherValue));
        assertThat(assignment1.merge(assignment2)).satisfies(merged -> {
            assertThat(merged.getValues()).containsExactlyInAnyOrderEntriesOf(Map.of(variable, value, anotherVariable, anotherValue));
        });
    }

    @Test
    void isSolution_true() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variable(variable)
                .build();
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.isConsistent(csp)).isTrue();
        assertThat(assignment.isSolution(csp)).isTrue();
    }

    @Test
    void isSolution_incomplete() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variable(variable)
                .variable(anotherVariable)
                .build();
        assertThat(assignment.isComplete(csp)).isFalse();
        assertThat(assignment.isConsistent(csp)).isTrue();
        assertThat(assignment.isSolution(csp)).isFalse();
    }

    @Test
    void isSolution_inconsistent() {
        when(variable.isAllowedValue(value)).thenReturn(true);
        var assignment = new Assignment(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variable(variable)
                .constraint(UnaryNotEqualsConstraint.of(variable, value))
                .build();
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.isConsistent(csp)).isFalse();
        assertThat(assignment.isSolution(csp)).isFalse();
    }
}
