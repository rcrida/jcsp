package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryNotEqualsConstraintTest {
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
    void getNeighbour() {
        assertThat(constraint.getNeighbour(left)).isEqualTo(right);
        assertThat(constraint.getNeighbour(right)).isEqualTo(left);
    }

    @Test
    void getNeighbour_incorrect() {
        assertThatThrownBy(() -> constraint.getNeighbour(VARIABLE_FACTORY.create("another")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(left, right), left != right>");
    }
}
