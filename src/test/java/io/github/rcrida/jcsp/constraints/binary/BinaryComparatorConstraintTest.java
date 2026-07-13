package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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

    @Test void propagate_bothDiscrete_noNarrowingNeeded() {
        // il=[1,5], ir=[1,5], il<=ir: il.max=min(5,5)=5 unchanged; ir.min=max(1,1)=1 unchanged
        Variable<Integer> il = F.create("il"), ir = F.create("ir");
        var result = BinaryComparatorConstraint.of(il, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(1, 5), ir, IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowsLeftMax() {
        // il=[0,10], ir=[3,8], il<=ir: il.max=min(10,8)=8 (prunes 9,10); ir.min=max(3,0)=3 unchanged
        Variable<Integer> il = F.create("il2"), ir = F.create("ir2");
        var result = BinaryComparatorConstraint.of(il, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(0, 10), ir, IntRangeDomain.of(3, 8))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(il)).toList()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(result.containsKey(ir)).isFalse();
    }

    @Test void propagate_bothDiscrete_infeasible() {
        // il=[5,10], ir=[0,3], il<=ir: il.max=min(10,3)=3 < il.min=5 -> infeasible
        Variable<Integer> il = F.create("il3"), ir = F.create("ir3");
        var result = BinaryComparatorConstraint.of(il, Operator.LEQ, ir)
                .propagate(Map.of(il, IntRangeDomain.of(5, 10), ir, IntRangeDomain.of(0, 3)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedLeftDomain_infeasible() {
        // l={0,10} (gap domain), r={4,5}, l==r: l narrows to [max(0,4),min(10,5)]=[4,5], a
        // non-empty numeric range, but neither 0 nor 10 lies in it -> narrowing empties l even
        // though the fast newLMin<=newLMax check alone would not have caught this. EQ narrows
        // both bounds symmetrically (unlike LEQ/GEQ, which anchor one bound at l's own extreme),
        // so a gap domain can empty here.
        Variable<Integer> l = F.create("l_gap"), r = F.create("r_gap");
        var lDomain = new IntRangeDomain(Set.of(0, 10));
        var rDomain = new IntRangeDomain(Set.of(4, 5));
        var result = BinaryComparatorConstraint.of(l, Operator.EQ, r).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedRightDomain_infeasible() {
        // l={4,5}, r={0,10} (gap domain), l==r: l is unchanged (already within [4,5]), but r
        // narrows to [4,5] and neither 0 nor 10 lies in it -> narrowing empties r this time
        Variable<Integer> l = F.create("l_gap2"), r = F.create("r_gap2");
        var lDomain = new IntRangeDomain(Set.of(4, 5));
        var rDomain = new IntRangeDomain(Set.of(0, 10));
        var result = BinaryComparatorConstraint.of(l, Operator.EQ, r).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_nonNumeric_noOp() {
        // Non-numeric Comparable pair (String): propagate() must not attempt a NumericBounds
        // conversion (which would throw ClassCastException) — the numeric guard keeps this a
        // no-op, same as before this class's discrete narrowing was added; ordering is enforced
        // purely by isSatisfiedBy plus AC3 during search.
        Variable<String> sl = F.create("sl"), sr = F.create("sr");
        var lDomain = io.github.rcrida.jcsp.domains.DomainObjectSet.<String>builder().value("a").value("b").build();
        var rDomain = io.github.rcrida.jcsp.domains.DomainObjectSet.<String>builder().value("x").value("y").build();
        var result = BinaryComparatorConstraint.of(sl, Operator.LEQ, sr).propagate(Map.of(sl, lDomain, sr, rDomain));
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

    // propagateWithReasons() tests

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagateWithReasons(domains(0.0, 10.0, 3.0, 8.0));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void propagateWithReasons_infeasible_leftSingleton_attributesLeft() {
        // L=[5,5] (singleton), R=[0,3]: L<=R is infeasible. Only L can be blamed on a specific
        // value; R is a genuine open range with nothing to pin the conflict on.
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagateWithReasons(domains(5.0, 5.0, 0.0, 3.0));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(L, 5.0)));
    }

    @Test void propagateWithReasons_infeasible_bothSingleton_attributesBoth() {
        // L=[5,5], R=[1,1]: L<=R is infeasible with both sides pinned to a concrete value.
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagateWithReasons(domains(5.0, 5.0, 1.0, 1.0));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(L, 5.0, R, 1.0)));
    }

    @Test void propagateWithReasons_infeasible_neitherSingleton_returnsEmptyReason() {
        // L=[5,10], R=[0,3]: infeasible, but neither side is pinned to a single value, so no
        // variable-value pair can be blamed — matches propagate_infeasible_returnsEmpty() above.
        var result = BinaryComparatorConstraint.of(L, Operator.LEQ, R).propagateWithReasons(domains(5.0, 10.0, 0.0, 3.0));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }
}
