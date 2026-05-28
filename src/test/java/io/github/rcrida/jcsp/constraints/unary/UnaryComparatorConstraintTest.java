package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryComparatorConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x");

    static Assignment a(int x) {
        return Assignment.of(Map.of(X, x));
    }

    @Test void eq_satisfied()  { assertThat(UnaryComparatorConstraint.of(X, Operator.EQ,  5).isSatisfiedBy(a(5))).isTrue(); }
    @Test void eq_violated()   { assertThat(UnaryComparatorConstraint.of(X, Operator.EQ,  5).isSatisfiedBy(a(4))).isFalse(); }
    @Test void neq_satisfied() { assertThat(UnaryComparatorConstraint.of(X, Operator.NEQ, 5).isSatisfiedBy(a(4))).isTrue(); }
    @Test void neq_violated()  { assertThat(UnaryComparatorConstraint.of(X, Operator.NEQ, 5).isSatisfiedBy(a(5))).isFalse(); }
    @Test void lt_satisfied()  { assertThat(UnaryComparatorConstraint.of(X, Operator.LT,  5).isSatisfiedBy(a(4))).isTrue(); }
    @Test void lt_violated()   { assertThat(UnaryComparatorConstraint.of(X, Operator.LT,  5).isSatisfiedBy(a(5))).isFalse(); }
    @Test void gt_satisfied()  { assertThat(UnaryComparatorConstraint.of(X, Operator.GT,  3).isSatisfiedBy(a(4))).isTrue(); }
    @Test void gt_violated()   { assertThat(UnaryComparatorConstraint.of(X, Operator.GT,  3).isSatisfiedBy(a(3))).isFalse(); }
    @Test void leq_satisfied() { assertThat(UnaryComparatorConstraint.of(X, Operator.LEQ, 5).isSatisfiedBy(a(5))).isTrue(); }
    @Test void leq_violated()  { assertThat(UnaryComparatorConstraint.of(X, Operator.LEQ, 5).isSatisfiedBy(a(6))).isFalse(); }
    @Test void geq_satisfied() { assertThat(UnaryComparatorConstraint.of(X, Operator.GEQ, 3).isSatisfiedBy(a(3))).isTrue(); }
    @Test void geq_violated()  { assertThat(UnaryComparatorConstraint.of(X, Operator.GEQ, 3).isSatisfiedBy(a(2))).isFalse(); }

    @Test
    void unassigned_optimisticallyTrue() {
        assertThat(UnaryComparatorConstraint.of(X, Operator.GEQ, 3).isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void getRelationIncludesOperatorSymbol() {
        assertThat(UnaryComparatorConstraint.of(X, Operator.GEQ, 3).getRelation()).contains(">=").contains("3");
    }
}
