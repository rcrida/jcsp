package org.jcsp.constraints.binary;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    void isSatisfiedBy_assignment(boolean result) {
        when(binaryRelation.isSatisfied(assignment)).thenReturn(result);
        assertThat(constraint.isSatisfiedBy(assignment)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isSatisfiedBy_values(boolean result) {
        val left = 10;
        val right = 5;
        when(binaryRelation.isSatisfied(left, right)).thenReturn(result);
        assertThat(constraint.isSatisfied(left, right)).isEqualTo(result);
    }

    @Test
    void reversed() {
        val reversed = constraint.reversed();
        assertThat(reversed.left()).isEqualTo(RIGHT);
        assertThat(reversed.right()).isEqualTo(LEFT);
        assertThat(reversed.relation()).isEqualTo(binaryRelation.reversed());
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(X, Y), binaryRelation>");
    }
}
