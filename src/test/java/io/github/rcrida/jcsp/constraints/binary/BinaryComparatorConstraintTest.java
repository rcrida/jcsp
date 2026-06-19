package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
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

    // propagate() tests for IntervalDomain
    static final Variable<Double> L = Variable.Factory.INSTANCE.create("l");
    static final Variable<Double> R = Variable.Factory.INSTANCE.create("r");

    static Map<Variable<?>, Domain<?>> domains(double lLo, double lHi, double rLo, double rHi) {
        return Map.of(L, IntervalDomain.of(lLo, lHi), R, IntervalDomain.of(rLo, rHi));
    }

    static IntervalDomain left(Map<Variable<?>, Domain<?>> m)  { return (IntervalDomain) m.get(L); }
    static IntervalDomain right(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(R); }

    @Test void propagate_leq_clipsLeftMax() {
        // L=[0,10], R=[3,8], L<=R: L.max clips to 8; R.min already >= L.min so R unchanged
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagate(domains(0.0, 10.0, 3.0, 8.0)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_leq_clipsRightMin() {
        // L=[2,7], R=[0,8], L<=R: R.min clips to 2; L.max=7 <= R.max=8 so L unchanged
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagate(domains(2.0, 7.0, 0.0, 8.0)).orElseThrow();
        assertThat(right(result).getMin()).isEqualTo(2.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_lt_sameAsLeq() {
        var result = BinaryComparatorConstraint.of(L, Operator.LT, R).propagate(domains(0.0, 10.0, 3.0, 8.0)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(8.0);
    }

    @Test void propagate_geq_clipsLeftMin() {
        // L=[0,10], R=[3,8], L>=R: L.min clips to 3; R.max already <= L.max so R unchanged
        var result = BinaryComparatorConstraint.of(L, Operator.GEQ, R).propagate(domains(0.0, 10.0, 3.0, 8.0)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(3.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_geq_clipsRightMax() {
        // L=[3,8], R=[0,10], L>=R: R.max clips to 8; L.min already >= R.min so L unchanged
        var result = BinaryComparatorConstraint.of(L, Operator.GEQ, R).propagate(domains(3.0, 8.0, 0.0, 10.0)).orElseThrow();
        assertThat(right(result).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_gt_sameAsGeq() {
        var result = BinaryComparatorConstraint.of(L, Operator.GT, R).propagate(domains(0.0, 10.0, 3.0, 8.0)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(3.0);
    }

    @Test void propagate_eq_intersectsDomains() {
        var c = BinaryComparatorConstraint.of(L, Operator.EQ, R);
        var result = c.propagate(domains(1.0, 8.0, 3.0, 10.0)).orElseThrow();
        assertThat(left(result).getMin()).isEqualTo(3.0);
        assertThat(left(result).getMax()).isEqualTo(8.0);
        assertThat(right(result).getMin()).isEqualTo(3.0);
        assertThat(right(result).getMax()).isEqualTo(8.0);
    }

    @Test void propagate_neq_noChange() {
        var result = BinaryComparatorConstraint.of(L, Operator.NEQ, R).propagate(domains(0.0, 10.0, 0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_infeasible_returnsEmpty() {
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagate(domains(5.0, 10.0, 0.0, 3.0));
        assertThat(result).isEmpty();
    }

    @Test void propagate_noChange_returnsEmptyMap() {
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagate(domains(0.0, 5.0, 0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_bothDiscrete_skipped() {
        Variable<Integer> il = F.create("il"), ir = F.create("ir");
        var result = BinaryComparatorConstraint.of(il, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(1, 5), ir, IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_leftBounded_rightDiscrete_clipsLeft() {
        // Left is IntervalDomain, right is discrete {3.0}: L.max clips to 3.
        Variable<Double> il = F.create("il_bd"), ir = F.create("ir_bd");
        var discreteDouble = io.github.rcrida.jcsp.domains.DomainObjectSet.<Double>builder().value(3.0).build();
        var result = BinaryComparatorConstraint.of(il, Operator.LEQ, ir)
                .propagate(Map.of(il, IntervalDomain.of(0.0, 10.0), ir, discreteDouble)).orElseThrow();
        assertThat(((IntervalDomain) result.get(il)).getMax()).isEqualTo(3.0);
        assertThat(result.containsKey(ir)).isFalse();
    }

    @Test void propagate_leftDiscrete_rightBounded_clipsRight() {
        // Left is discrete {2.0, 7.0}, right is IntervalDomain[0,8]: R.min clips to lMin=2.
        var discreteLeft = io.github.rcrida.jcsp.domains.DomainObjectSet.<Double>builder().value(2.0).value(7.0).build();
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R)
                .propagate(Map.of(L, discreteLeft, R, IntervalDomain.of(0.0, 8.0))).orElseThrow();
        assertThat(right(result).getMin()).isEqualTo(2.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_leftDiscrete_rightBounded_infeasible() {
        // Left discrete {5.0, 10.0}, right=Interval[0,3], L<=R: lMin=5 > rMax=3 → infeasible.
        var discreteLeft = io.github.rcrida.jcsp.domains.DomainObjectSet.<Double>builder().value(5.0).value(10.0).build();
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R)
                .propagate(Map.of(L, discreteLeft, R, IntervalDomain.of(0.0, 3.0)));
        assertThat(result).isEmpty();
    }
}
