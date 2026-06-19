package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
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

    // propagate() tests for IntervalDomain
    static final Variable<Double> DX = Variable.Factory.INSTANCE.create("dx");

    static Map<Variable<?>, Domain<?>> domains(double lo, double hi) {
        return Map.of(DX, IntervalDomain.of(lo, hi));
    }

    static IntervalDomain narrowed(Map<Variable<?>, Domain<?>> result) {
        return (IntervalDomain) result.get(DX);
    }

    @Test void propagate_geq_clipsMin() {
        var result = UnaryComparatorConstraint.of(DX, Operator.GEQ, 3.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(narrowed(result.get()).getMin()).isEqualTo(3.0);
        assertThat(narrowed(result.get()).getMax()).isEqualTo(10.0);
    }

    @Test void propagate_gt_clipsMin() {
        var result = UnaryComparatorConstraint.of(DX, Operator.GT, 3.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(narrowed(result.get()).getMin()).isEqualTo(3.0);
    }

    @Test void propagate_leq_clipsMax() {
        var result = UnaryComparatorConstraint.of(DX, Operator.LEQ, 7.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(narrowed(result.get()).getMax()).isEqualTo(7.0);
        assertThat(narrowed(result.get()).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_lt_clipsMax() {
        var result = UnaryComparatorConstraint.of(DX, Operator.LT, 7.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(narrowed(result.get()).getMax()).isEqualTo(7.0);
    }

    @Test void propagate_eq_pinsDomain() {
        var result = UnaryComparatorConstraint.of(DX, Operator.EQ, 5.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(narrowed(result.get()).getMin()).isEqualTo(5.0);
        assertThat(narrowed(result.get()).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_neq_noChange() {
        var result = UnaryComparatorConstraint.of(DX, Operator.NEQ, 5.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_infeasible_returnsEmpty() {
        var result = UnaryComparatorConstraint.of(DX, Operator.GEQ, 20.0).propagate(domains(0.0, 10.0));
        assertThat(result).isEmpty();
    }

    @Test void propagate_noChange_returnsEmptyMap() {
        var result = UnaryComparatorConstraint.of(DX, Operator.GEQ, 0.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_discreteDomain_skipped() {
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 3).propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }
}
