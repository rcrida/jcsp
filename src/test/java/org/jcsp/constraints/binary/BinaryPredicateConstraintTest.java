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
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BinaryPredicateConstraintTest {
    @Mock
    Variable variable1;
    @Mock
    Variable variable2;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    BiPredicate<Object, Object> biPredicate;
    @Mock
    BiPredicate<Object, Object> anotherBiPredicate;
    BinaryPredicateConstraint binaryPredicateConstraint;

    @BeforeEach
    void setUp() {
        binaryPredicateConstraint = BinaryPredicateConstraint.builder().left(variable1).right(variable2).biPredicate(biPredicate).build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy(boolean satisfied) {
        var assignment = Assignment.of(Map.of(variable1, value1, variable2, value2));
        when(biPredicate.test(value1, value2)).thenReturn(satisfied);
        assertThat(binaryPredicateConstraint.isSatisfiedBy(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(binaryPredicateConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1)))).isTrue();
        assertThat(binaryPredicateConstraint.isSatisfiedBy(Assignment.of(Map.of(variable2, value2)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(binaryPredicateConstraint.toString()).isEqualTo("<(variable1, variable2), biPredicate>");
    }

    @Test
    void equals() {
        val anotherBinaryExpressionConstraint = BinaryPredicateConstraint.builder().left(variable1).right(variable2).biPredicate(anotherBiPredicate).build();
        assertThat(binaryPredicateConstraint).isNotEqualTo(anotherBinaryExpressionConstraint);
        val sameBinaryExpressionConstraint = BinaryPredicateConstraint.builder().left(variable1).right(variable2).biPredicate(biPredicate).build();
        assertThat(binaryPredicateConstraint).isEqualTo(sameBinaryExpressionConstraint);
    }
}
