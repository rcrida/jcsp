package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        allDiffConstraint = AllDiffConstraint.builder().variables(Set.of(variable1, variable2, variable3)).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingle() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleDifferent() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleSame() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value1)))).isFalse();
    }

    @Test
    void isSatisfiedByTripleDifferent() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2, variable3, value3)))).isTrue();
    }

    @Test
    void isSatisfiedByTripleSame() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2, variable3, value2)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(allDiffConstraint.toString()).isEqualTo("<(variable1, variable2, variable3), AllDiff>");
    }
}
