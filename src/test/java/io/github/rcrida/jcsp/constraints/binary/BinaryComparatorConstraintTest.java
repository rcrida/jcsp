package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryComparatorConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> LEFT  = F.create("left");
    static final Variable<Integer> RIGHT = F.create("right");

    static Assignment a(int l, int r) { return Assignment.of(Map.of(LEFT, l, RIGHT, r)); }

    @Test void eq_satisfied()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.EQ,  RIGHT).isSatisfiedBy(a(3, 3))).isTrue(); }
    @Test void eq_violated()   { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.EQ,  RIGHT).isSatisfiedBy(a(3, 4))).isFalse(); }
    @Test void neq_satisfied() { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.NEQ, RIGHT).isSatisfiedBy(a(3, 4))).isTrue(); }
    @Test void neq_violated()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.NEQ, RIGHT).isSatisfiedBy(a(3, 3))).isFalse(); }
    @Test void lt_satisfied()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LT,  RIGHT).isSatisfiedBy(a(2, 3))).isTrue(); }
    @Test void lt_violated()   { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LT,  RIGHT).isSatisfiedBy(a(3, 3))).isFalse(); }
    @Test void gt_satisfied()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.GT,  RIGHT).isSatisfiedBy(a(4, 3))).isTrue(); }
    @Test void gt_violated()   { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.GT,  RIGHT).isSatisfiedBy(a(3, 3))).isFalse(); }
    @Test void leq_satisfied() { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT).isSatisfiedBy(a(3, 3))).isTrue(); }
    @Test void leq_violated()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT).isSatisfiedBy(a(4, 3))).isFalse(); }
    @Test void geq_satisfied() { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.GEQ, RIGHT).isSatisfiedBy(a(3, 3))).isTrue(); }
    @Test void geq_violated()  { assertThat(BinaryComparatorConstraint.of(LEFT, Operator.GEQ, RIGHT).isSatisfiedBy(a(2, 3))).isFalse(); }

    @Test
    void partialAssignment_optimisticallyTrue() {
        assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT).isSatisfiedBy(Assignment.of(Map.of(LEFT, 5)))).isTrue();
        assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT).isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT).toString())
                .isEqualTo("<(left, right), left <= right>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT))
                .isEqualTo(BinaryComparatorConstraint.of(LEFT, Operator.LEQ, RIGHT));
    }
}
