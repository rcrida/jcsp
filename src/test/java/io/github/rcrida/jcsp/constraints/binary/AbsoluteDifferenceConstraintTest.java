package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AbsoluteDifferenceConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> L = F.create("l_abs");
    static final Variable<Double> R = F.create("r_abs");

    static Map<Variable<?>, Domain<?>> intervals(double lLo, double lHi, double rLo, double rHi) {
        return Map.of(L, IntervalDomain.of(lLo, lHi), R, IntervalDomain.of(rLo, rHi));
    }
    static IntervalDomain left(Map<Variable<?>, Domain<?>> m)  { return (IntervalDomain) m.get(L); }
    static IntervalDomain right(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(R); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_leq_withinBound() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).isSatisfiedBy(5.0, 7.0)).isTrue();
    }

    @Test void isSatisfiedBy_leq_atBound() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).isSatisfiedBy(4.0, 7.0)).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).isSatisfiedBy(1.0, 5.0)).isFalse();
    }

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 3.0).isSatisfiedBy(2.0, 5.0)).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 3.0).isSatisfiedBy(1.0, 5.0)).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 3.0).isSatisfiedBy(1.0, 5.0)).isTrue();
    }

    @Test void isSatisfiedBy_geq_atBound() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 3.0).isSatisfiedBy(2.0, 5.0)).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 3.0).isSatisfiedBy(5.0, 7.0)).isFalse();
    }

    @Test void isSatisfiedBy_neq_nonZeroDiff() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.NEQ, 0.0).isSatisfiedBy(3.0, 5.0)).isTrue();
    }

    @Test void isSatisfiedBy_neq_zeroDiff() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.NEQ, 0.0).isSatisfiedBy(5.0, 5.0)).isFalse();
    }

    @Test void isSatisfiedBy_symmetricForLeftMinusRight() {
        // |7 - 4| == |4 - 7| == 3
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).isSatisfiedBy(7.0, 4.0)).isTrue();
    }

    // --- toString ---

    @Test void toString_format() {
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).toString())
                .isEqualTo("<(l_abs, r_abs), |l_abs - r_abs| <= 3.0>");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var a = AbsoluteDifferenceConstraint.<Double>builder().left(L).right(R).operator(Operator.LEQ).bound(3.0).build();
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0)).isEqualTo(a);
    }

    // --- propagate: NEQ skipped ---

    @Test void propagate_neq_noChange() {
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.NEQ, 0.0).propagate(intervals(0, 10, 0, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: both discrete ---

    @Test void propagate_bothDiscrete_noNarrowingNeeded() {
        // il=[0,10], ir=[0,10], |il-ir|<=3: il∈[max(0,-3),min(10,13)]=[0,10] unchanged;
        // ir∈[max(0,-3),min(10,13)]=[0,10] unchanged — both ranges already tight enough
        Variable<Integer> il = F.create("il"), ir = F.create("ir");
        var result = AbsoluteDifferenceConstraint.of(il, ir, Operator.LEQ, 3)
                .propagate(Map.of(il, IntRangeDomain.of(0, 10), ir, IntRangeDomain.of(0, 10)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowsLeftMax() {
        // il=[0,10], ir=[0,4], |il-ir|<=3: il.max=min(10,4+3)=7 (prunes 8,9,10); ir unchanged
        Variable<Integer> il = F.create("il2"), ir = F.create("ir2");
        var result = AbsoluteDifferenceConstraint.of(il, ir, Operator.LEQ, 3)
                .propagate(Map.of(il, IntRangeDomain.of(0, 10), ir, IntRangeDomain.of(0, 4))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(il)).toList()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(result.containsKey(ir)).isFalse();
    }

    @Test void propagate_bothDiscrete_infeasible() {
        // il=[8,10], ir=[0,3], |il-ir|<=3: il.max=min(10,3+3)=6 < il.min=8 -> infeasible
        Variable<Integer> il = F.create("il3"), ir = F.create("ir3");
        var result = AbsoluteDifferenceConstraint.of(il, ir, Operator.LEQ, 3)
                .propagate(Map.of(il, IntRangeDomain.of(8, 10), ir, IntRangeDomain.of(0, 3)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedLeftDomain_infeasible() {
        // l={0,10} (gap domain), r={4,5}, |l-r|<=1: l narrows to [max(0,3),min(10,6)]=[3,6], a
        // non-empty numeric range, but neither 0 nor 10 lies in it -> narrowing empties l even
        // though the fast newLMin<=newLMax check alone would not have caught this
        Variable<Integer> l = F.create("l_gap"), r = F.create("r_gap");
        var lDomain = new IntRangeDomain(Set.of(0, 10));
        var rDomain = new IntRangeDomain(Set.of(4, 5));
        var result = AbsoluteDifferenceConstraint.of(l, r, Operator.LEQ, 1).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_narrowingEmptiesGappedRightDomain_infeasible() {
        // l={4,5}, r={0,10} (gap domain), |l-r|<=1: l is unchanged (already within [4,5]), but r
        // narrows to [3,6] and neither 0 nor 10 lies in it -> narrowing empties r this time
        Variable<Integer> l = F.create("l_gap2"), r = F.create("r_gap2");
        var lDomain = new IntRangeDomain(Set.of(4, 5));
        var rDomain = new IntRangeDomain(Set.of(0, 10));
        var result = AbsoluteDifferenceConstraint.of(l, r, Operator.LEQ, 1).propagate(Map.of(l, lDomain, r, rDomain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_bothDiscrete_geq_infeasible() {
        // il=[4,6], ir=[4,6], |il-ir|>=5: max dist = 2 < 5 -> infeasible, now detected for
        // discrete/discrete pairs too instead of being skipped entirely
        Variable<Integer> il = F.create("il4"), ir = F.create("ir4");
        var result = AbsoluteDifferenceConstraint.of(il, ir, Operator.GEQ, 5)
                .propagate(Map.of(il, IntRangeDomain.of(4, 6), ir, IntRangeDomain.of(4, 6)));
        assertThat(result).isEmpty();
    }

    // --- propagate: LEQ ---

    @Test void propagate_leq_clipsLeftMax() {
        // L=[0,10], R=[0,4], |L-R|<=3: L.max=min(10, 4+3)=7; R unchanged
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagate(intervals(0, 10, 0, 4)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(7.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_leq_clipsRightMax() {
        // L=[0,4], R=[0,10], |L-R|<=3: R.max=min(10, 4+3)=7; L unchanged
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagate(intervals(0, 4, 0, 10)).orElseThrow();
        assertThat(right(result).getMax()).isEqualTo(7.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_leq_clipsLeftMinAndRightMin() {
        // L=[6,10], R=[0,10], |L-R|<=3: L.max=min(10,13)=10 unchanged; R.min=max(0,6-3)=3; L.min=max(6,0-3)=6 unchanged; R.max=min(10,10+3)=10 unchanged
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagate(intervals(6, 10, 0, 10)).orElseThrow();
        assertThat(right(result).getMin()).isEqualTo(3.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_leq_noChange() {
        // L=[0,5], R=[0,5], |L-R|<=10: bounds already within proximity, no change
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 10.0).propagate(intervals(0, 5, 0, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_lt_sameAsLeq() {
        // LT behaves same as LEQ for interval arithmetic
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LT, 3.0).propagate(intervals(0, 10, 0, 4)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(7.0);
    }

    @Test void propagate_leq_infeasible() {
        // L=[8,10], R=[0,3], |L-R|<=3: min dist = 8-3=5 > 3 → infeasible
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagate(intervals(8, 10, 0, 3))).isEmpty();
    }

    // --- propagate: GEQ/GT ---

    @Test void propagate_geq_feasible_noNarrowing() {
        // L=[0,10], R=[0,10], |L-R|>=3: max dist=10 >= 3 → feasible, no narrowing
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 3.0).propagate(intervals(0, 10, 0, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_geq_infeasible() {
        // L=[4,6], R=[4,6], |L-R|>=5: max dist = max(6-4, 6-4) = 2 < 5 → infeasible
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 5.0).propagate(intervals(4, 6, 4, 6))).isEmpty();
    }

    @Test void propagate_gt_infeasible() {
        // L=[3,5], R=[3,5], |L-R|>3: max dist = 2 < 3 → infeasible (same threshold applies)
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GT, 3.0).propagate(intervals(3, 5, 3, 5))).isEmpty();
    }

    @Test void propagate_gt_feasible() {
        // L=[0,10], R=[5,5], |L-R|>3: max dist = max(10-5, 5-0) = 5 > 3 → feasible
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.GT, 3.0).propagate(intervals(0, 10, 5, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_gt_infeasible_atBoundary() {
        // L=[0,4], R=[4,4]: max dist = max(4-4, 4-0) = 4; |L-R|>4 requires strictly > 4 → infeasible
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.GT, 4.0).propagate(intervals(0, 4, 4, 4))).isEmpty();
    }

    @Test void propagate_geq_feasible_atBoundary() {
        // L=[0,4], R=[4,4]: max dist = 4; |L-R|>=4 is satisfiable at exactly 4 → feasible
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 4.0).propagate(intervals(0, 4, 4, 4));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_narrowsBothBounds() {
        // L=[0,10], R=[0,5], |L-R|==3: proximity narrowing: L∈[0-3,5+3]=[0,8]∩[0,10]=[0,8]; R∈[0-3,10+3]=[0,10]∩[0,5]=[0,5]
        // max dist = max(10-0, 5-0)=10 >= 3 → feasible; L.max narrows to 8
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 3.0).propagate(intervals(0, 10, 0, 5)).orElseThrow();
        assertThat(left(result).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_eq_infeasible_tooFar() {
        // L=[0,1], R=[8,9], |L-R|==3: proximity: L∈[5,12]∩[0,1]=empty → infeasible
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 3.0).propagate(intervals(0, 1, 8, 9))).isEmpty();
    }

    @Test void propagate_eq_infeasible_tooClose() {
        // L=[4,6], R=[4,6], |L-R|==5: max dist = 2 < 5 → infeasible
        assertThat(AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 5.0).propagate(intervals(4, 6, 4, 6))).isEmpty();
    }

    // --- propagate: mixed bounded/discrete ---

    @Test void propagate_leftBounded_rightDiscrete_clipsLeft() {
        // L=Interval[0,10], R={8.0}, |L-R|<=3: L∈[8-3, 8+3]=[5,11]∩[0,10]=[5,10]
        var discrete = DomainObjectSet.<Double>builder().value(8.0).build();
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0)
                .propagate(Map.of(L, IntervalDomain.of(0.0, 10.0), R, discrete)).orElseThrow();
        assertThat(((IntervalDomain) result.get(L)).getMin()).isEqualTo(5.0);
        assertThat(((IntervalDomain) result.get(L)).getMax()).isEqualTo(10.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_leftDiscrete_rightBounded_clipsRight() {
        // L={2.0,5.0}, R=Interval[0,10], |L-R|<=3: R∈[2-3,5+3]=[-1,8]∩[0,10]=[0,8]
        var discrete = DomainObjectSet.<Double>builder().value(2.0).value(5.0).build();
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0)
                .propagate(Map.of(L, discrete, R, IntervalDomain.of(0.0, 10.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(R)).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    // --- propagateWithReasons() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagateWithReasons(intervals(0, 10, 0, 4));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_leq_infeasible_leftSingleton_attributesLeft() {
        // L=[8,8] (singleton), R=[0,3], |L-R|<=3 is infeasible (min dist 5 > 3). Only L can be
        // blamed on a specific value; R is a genuine open range with nothing to pin the conflict on.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagateWithReasons(intervals(8, 8, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsExactly(Map.entry(L, 8.0));
    }

    @Test void propagateWithReasons_leq_infeasible_bothSingleton_attributesBoth() {
        // L=[8,8], R=[0,0]: |L-R|<=3 is infeasible with both sides pinned to a concrete value.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagateWithReasons(intervals(8, 8, 0, 0));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(L, 8.0), Map.entry(R, 0.0));
    }

    @Test void propagateWithReasons_leq_infeasible_neitherSingleton_returnsEmptyReason() {
        // L=[8,10], R=[0,3]: infeasible, but neither side is pinned to a single value, so no
        // variable-value pair can be blamed — matches propagate_leq_infeasible() above.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.LEQ, 3.0).propagateWithReasons(intervals(8, 10, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_geq_infeasible_bothSingleton_attributesBoth() {
        // L=[5,5], R=[5,5]: |L-R|>=3 is infeasible (max dist 0 < 3), both sides singleton.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 3.0).propagateWithReasons(intervals(5, 5, 5, 5));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(L, 5.0), Map.entry(R, 5.0));
    }

    @Test void propagateWithReasons_geq_infeasible_neitherSingleton_returnsEmptyReason() {
        // L=[4,6], R=[4,6]: infeasible (max dist 2 < 5), matches propagate_geq_infeasible() above.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.GEQ, 5.0).propagateWithReasons(intervals(4, 6, 4, 6));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_eq_infeasible_tooFar_attributesSingletons() {
        // L=[0,0], R=[9,9], |L-R|==3: proximity narrowing crosses (newLMin=6 > newLMax=0),
        // matching propagate_eq_infeasible_tooFar() above. Both sides are singleton so both
        // are attributed.
        var result = AbsoluteDifferenceConstraint.of(L, R, Operator.EQ, 3.0).propagateWithReasons(intervals(0, 0, 9, 9));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(L, 0.0), Map.entry(R, 9.0));
    }
}
