package org.jcsp.solver.backtrackingsearch.selector;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.solver.backtrackingsearch.selector.MinimumRemainingValuesSelector;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MinimumRemainingValuesSelectorTest {

    @Mock
    ConstraintSatisfactionProblem csp;
    @Mock
    Assignment assignment;
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Variable variable3;
    @Mock
    Domain domain1;
    @Mock
    Domain domain2;
    @Mock
    Domain domain3;

    MinimumRemainingValuesSelector selector = MinimumRemainingValuesSelector.INSTANCE;

    @Test
    void testSelectReturnsVariableWithSmallestRemainingDomain() {
        // Mock getVariableDomains behavior
        when(csp.getVariableDomains()).thenReturn(Map.of(
                variable1, domain1,
                variable2, domain2,
                variable3, domain3
        ));

        // Mock assignment behavior
        when(assignment.getValue(variable1)).thenReturn(Optional.empty());
        when(assignment.getValue(variable2)).thenReturn(Optional.empty());
        when(assignment.getValue(variable3)).thenReturn(Optional.empty());

        // Mock domain size
        when(domain1.stream()).thenReturn((Stream) Stream.of(1, 2, 3));  // Size 3
        when(domain2.stream()).thenReturn((Stream) Stream.of(1, 2));     // Size 2
        when(domain3.stream()).thenReturn((Stream) Stream.of(1, 2, 3, 4)); // Size 4

        // Act
        Variable selected = selector.select(csp, assignment);

        // Assert
        assertEquals(variable2, selected, "Variable with smallest domain should be selected.");
    }

    @Test
    void testSelectThrowsExceptionWhenAllVariablesAreAssigned() {
        // Mock getVariableDomains behavior
        when(csp.getVariableDomains()).thenReturn(Map.of(
                variable1, domain1,
                variable2, domain2
        ));

        // Mock all variables assigned
        when(assignment.getValue(variable1)).thenReturn(Optional.of("Value1"));
        when(assignment.getValue(variable2)).thenReturn(Optional.of("Value2"));

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> selector.select(csp, assignment));
        assertEquals("No unassigned variable found", exception.getMessage());
    }

    @Test
    void testSelectThrowsExceptionWhenNoUnassignedVariablesAvailable() {
        // Mock empty variable domain
        when(csp.getVariableDomains()).thenReturn(Map.of());

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> selector.select(csp, assignment));
        assertEquals("No unassigned variable found", exception.getMessage());
    }
}
