package org.jcsp.constraints;

import lombok.val;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.relations.BinaryRelation;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BinaryConstraintTest {
    static final Domain DOMAIN = new IntRangeDomain(0, 10);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};
    static final Variable LEFT = VARIABLE_FACTORY.create("X", DOMAIN);
    static final Variable RIGHT = VARIABLE_FACTORY.create("Y", DOMAIN);

    @Mock
    BinaryRelation binaryRelation;
    @Mock
    Assignment assignment;
    BinaryConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryConstraint.of(LEFT, RIGHT, binaryRelation);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfied_assignment(boolean result) {
        when(binaryRelation.isSatisfied(assignment)).thenReturn(result);
        assertThat(constraint.isSatisfied(assignment)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfied_array(boolean result) {
        val left = 10;
        val right = 5;
        when(binaryRelation.isSatisfied(left, right)).thenReturn(result);
        assertThat(constraint.isSatisfied(new Integer[] {left, right})).isEqualTo(result);
    }

    static Stream<int[]> isSatisfied_arrayAsserts() {
        return Stream.of(
                new int[] {},
                new int[] {1},
                new int[] {1, 2, 3}
        );
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfied_arrayAsserts(int[] numbers) {
        val objects = Arrays.stream(numbers).boxed().toArray();
        assertThatThrownBy(() -> constraint.isSatisfied(objects))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Binary constraint requires exactly two values");
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(X, Y), binaryRelation>");
    }
}
