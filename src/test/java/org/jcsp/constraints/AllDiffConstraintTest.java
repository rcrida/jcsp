package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AllDiffConstraintTest {
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Variable variable3;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    Object value3;
    @Mock
    Domain domain;
    AllDiffConstraint allDiffConstraint;

    @BeforeEach
    void setUp() {
        allDiffConstraint = new AllDiffConstraint(Set.of(variable1, variable2, variable3));
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingle() {
        when(domain.contains(value1)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of(variable1, value1)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleDifferent() {
        when(domain.contains(value1)).thenReturn(true);
        when(domain.contains(value2)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable2.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        when(variable2.isAllowedValue(value2)).thenCallRealMethod();
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of(variable1, value1, variable2, value2)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleSame() {
        when(domain.contains(value1)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable2.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        when(variable2.isAllowedValue(value1)).thenCallRealMethod();
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of(variable1, value1, variable2, value1)))).isFalse();
    }

    @Test
    void isSatisfiedByTripleDifferent() {
        when(domain.contains(value1)).thenReturn(true);
        when(domain.contains(value2)).thenReturn(true);
        when(domain.contains(value3)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable2.getDomain()).thenReturn(domain);
        when(variable3.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        when(variable2.isAllowedValue(value2)).thenCallRealMethod();
        when(variable3.isAllowedValue(value3)).thenCallRealMethod();
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of(variable1, value1, variable2, value2, variable3, value3)))).isTrue();
    }

    @Test
    void isSatisfiedByTripleSame() {
        when(domain.contains(value1)).thenReturn(true);
        when(domain.contains(value2)).thenReturn(true);
        when(variable1.getDomain()).thenReturn(domain);
        when(variable2.getDomain()).thenReturn(domain);
        when(variable3.getDomain()).thenReturn(domain);
        when(variable1.isAllowedValue(value1)).thenCallRealMethod();
        when(variable2.isAllowedValue(value2)).thenCallRealMethod();
        when(variable3.isAllowedValue(value2)).thenCallRealMethod();
        assertThat(allDiffConstraint.isSatisfiedBy(new Assignment(Map.of(variable1, value1, variable2, value2, variable3, value2)))).isFalse();
    }
}
