package org.jcsp.constraints.binary;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryNotEqualsConstraintTest {
    static final Domain DOMAIN = new IntRangeDomain(0, 10);
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable left = VARIABLE_FACTORY.create("left");
    Variable right = VARIABLE_FACTORY.create("right");
    BinaryNotEqualsConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryNotEqualsConstraint.builder()
                .left(left)
                .right(right)
                .build();
    }

    @Test
    void isSatisfied_true() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 1)))).isTrue();
    }

    @Test
    void isSatisfied_false() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0, right, 0)))).isFalse();
    }

    @Test
    void isSatisfied_unknowns() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, 0)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(right, 1)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(left, right), left != right>");
    }
}
