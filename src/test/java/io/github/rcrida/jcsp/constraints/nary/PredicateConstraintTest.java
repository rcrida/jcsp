package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PredicateConstraintTest {
    @Mock
    Variable<Object> variable1;
    @Mock
    Variable<Object> variable2;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    Domain domain;
    @Mock
    Predicate<Assignment> predicate;
    PredicateConstraint predicateConstraint;

    @BeforeEach
    void setUp() {
        predicateConstraint = PredicateConstraint.builder().variables(Set.of(variable1, variable2)).predicate(predicate).build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy(boolean satisfied) {
        var assignment = Assignment.of(Map.of(variable1, value1, variable2, value2));
        when(predicate.test(assignment)).thenReturn(satisfied);
        assertThat(predicateConstraint.isSatisfiedBy(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(predicateConstraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(predicateConstraint.toString()).isEqualTo("<(variable1, variable2), predicate>");
    }
}
