package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryAtMostOneConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Boolean> left  = F.create("left");
    Variable<Boolean> right = F.create("right");
    BinaryAtMostOneConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryAtMostOneConstraint.of(left, right);
    }

    @Test
    void bothFalse_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, false, right, false)))).isTrue();
    }

    @Test
    void leftTrue_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, true, right, false)))).isTrue();
    }

    @Test
    void rightTrue_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, false, right, true)))).isTrue();
    }

    @Test
    void bothTrue_violated() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, true, right, true)))).isFalse();
    }

    @Test
    void partialAssignment_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(left, true)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void symmetric_equalWithSwappedVariables() {
        var swapped = BinaryAtMostOneConstraint.of(right, left);
        assertThat(constraint).isEqualTo(swapped);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(left, right), AtMostOne>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryAtMostOneConstraint.of(left, right)).isEqualTo(constraint);
    }
}
