package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RandomVariableSelectorTest {

    @Mock ConstraintSatisfactionProblem csp;
    @Mock Assignment assignment;
    @Mock Variable variable1;
    @Mock Variable variable2;
    @Mock Variable variable3;
    @Mock Domain domain1;
    @Mock Domain domain2;
    @Mock Domain domain3;

    RandomVariableSelector selector = RandomVariableSelector.INSTANCE;

    @Test
    void selectReturnsVariableFromCsp() {
        when(csp.getVariableDomains()).thenReturn(Map.of(variable1, domain1, variable2, domain2));

        var selected = selector.select(csp, assignment);

        assertTrue(Set.of(variable1, variable2).contains(selected));
    }

    @Test
    void selectCoversAllVariablesOverManyIterations() {
        when(csp.getVariableDomains()).thenReturn(Map.of(
                variable1, domain1,
                variable2, domain2,
                variable3, domain3));

        Set<Variable<?>> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(selector.select(csp, assignment));
        }

        assertEquals(Set.of(variable1, variable2, variable3), seen);
    }

    @Test
    void selectThrowsWhenNoVariables() {
        when(csp.getVariableDomains()).thenReturn(Map.of());

        assertThrows(IllegalArgumentException.class, () -> selector.select(csp, assignment));
    }
}
