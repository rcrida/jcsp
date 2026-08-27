package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AbsoluteDifferenceVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> L = F.create("l_adv");
    static final Variable<Double> R = F.create("r_adv");
    static final Variable<Double> T = F.create("t_adv");

    static AbsoluteDifferenceVariableConstraint<Double> of(Operator operator) {
        return AbsoluteDifferenceVariableConstraint.of(L, R, operator, T);
    }

    static Map<Variable<?>, Domain<?>> domains(double lLo, double lHi, double rLo, double rHi, double tLo, double tHi) {
        return Map.of(L, IntervalDomain.of(lLo, lHi), R, IntervalDomain.of(rLo, rHi), T, IntervalDomain.of(tLo, tHi));
    }

    static IntervalDomain lDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(L); }
    static IntervalDomain rDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(R); }
    static IntervalDomain tDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(T); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 5.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 5.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0, T, 5.0)))).isFalse();
    }

    @Test void isSatisfiedBy_symmetricForLeftMinusRight() {
        // |4 - 7| == |7 - 4| == 3
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(L, 4.0, R, 7.0, T, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_targetUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(L, 7.0, R, 4.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leftUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(R, 4.0, T, 100.0)))).isTrue();
    }

    // --- toString / of() ---

    @Test void testToString() {
        assertThat(of(Operator.LEQ).toString()).isEqualTo("<(l_adv, r_adv, t_adv), |l_adv - r_adv| <= t_adv>");
    }

    @Test void of_createsEquivalentConstraint() {
        var built = AbsoluteDifferenceVariableConstraint.<Double>builder()
                .variables(Set.of(L, R, T)).left(L).right(R).operator(Operator.LEQ).target(T).build();
        assertThat(AbsoluteDifferenceVariableConstraint.of(L, R, Operator.LEQ, T)).isEqualTo(built);
    }

    // --- propagate: LT/GT/NEQ skipped ---

    @Test void propagate_lt_returnsEmptyMap() {
        var result = of(Operator.LT).propagate(domains(0, 10, 0, 10, 0, 20));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_neq_returnsEmptyMap() {
        var result = of(Operator.NEQ).propagate(domains(0, 10, 0, 10, 0, 20));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: EQ (target narrowing both directions) ---

    @Test void propagate_eq_narrowsTargetUpperBound() {
        // L=[0,10], R=[0,4]: diff range [-4,10] straddles zero -> dLo=0, dHi=max(4,10)=10.
        // T=[0,20] -> newTLo=max(0,0)=0 (unchanged), newTHi=min(20,10)=10 (clipped).
        var result = of(Operator.EQ).propagate(domains(0, 10, 0, 4, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMax()).isEqualTo(10.0);
        assertThat(tDom(result).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_eq_narrowsLeftMaxUsingTargetBound() {
        // L=[0,20], R=[0,5], T=[0,3]: dist <= target's current max (3) means L <= R.max + 3 = 8
        // (clips L from 20 down to 8) and R <= L.min + 3 = 3 (already within [0,5]? no -- R.max
        // clips from 5 down to min(5, 20+3)=5, unchanged). Only L narrows here.
        var result = of(Operator.EQ).propagate(domains(0, 20, 0, 5, 0, 3)).orElseThrow();
        assertThat(lDom(result).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_eq_narrowsRightMaxUsingTargetBound() {
        // Mirror of the above with L/R roles swapped: R clips from 20 down to 8, L unchanged.
        var result = of(Operator.EQ).propagate(domains(0, 5, 0, 20, 0, 3)).orElseThrow();
        assertThat(rDom(result).getMax()).isEqualTo(8.0);
        assertThat(result.containsKey(L)).isFalse();
    }

    @Test void propagate_eq_infeasible_targetTooLow() {
        // L=[0,1], R=[10,11]: diff range [-11,-9] doesn't straddle zero -> dLo=min(11,9)=9,
        // dHi=max(11,9)=11. T=[0,5]: dLo(9) > tHi(5) -> infeasible.
        assertThat(of(Operator.EQ).propagate(domains(0, 1, 10, 11, 0, 5))).isEmpty();
    }

    @Test void propagate_eq_infeasible_targetTooHigh() {
        // L=[0,1], R=[0,1]: diff range [-1,1] straddles zero -> dLo=0, dHi=1.
        // T=[5,10]: dHi(1) < tLo(5) -> infeasible.
        assertThat(of(Operator.EQ).propagate(domains(0, 1, 0, 1, 5, 10))).isEmpty();
    }

    // --- propagate: LEQ (target lower bound only, plus left/right conjunctive narrowing) ---

    @Test void propagate_leq_raisesTargetLowerBound() {
        // L=[0,1], R=[10,11]: dLo=9, dHi=11 (as above). T=[0,20], LEQ -> newTLo=max(0,9)=9
        // (raised); newTHi stays 20 (GEQ-side narrowing skipped for LEQ).
        var result = of(Operator.LEQ).propagate(domains(0, 1, 10, 11, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(9.0);
        assertThat(tDom(result).getMax()).isEqualTo(20.0);
    }

    @Test void propagate_leq_narrowsLeftMax() {
        // Same numbers as propagate_eq_narrowsLeftMaxUsingTargetBound -- LEQ gets the same
        // conjunctive left/right narrowing EQ does.
        var result = of(Operator.LEQ).propagate(domains(0, 20, 0, 5, 0, 3)).orElseThrow();
        assertThat(lDom(result).getMax()).isEqualTo(8.0);
    }

    @Test void propagate_leq_infeasible() {
        // dLo(9) > tHi: same as propagate_eq_infeasible_targetTooLow but LEQ-only.
        assertThat(of(Operator.LEQ).propagate(domains(0, 1, 10, 11, 0, 5))).isEmpty();
    }

    @Test void propagate_leq_noChange() {
        var result = of(Operator.LEQ).propagate(domains(0, 5, 0, 5, 0, 20));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_eq_narrowingEmptiesGappedTargetDomain_infeasible() {
        // Regression test for a real bug found via GracefulGraph-K02-P04.xml.lzma: l=[0,1], r=[10,11]
        // give dLo=9, dHi=11 (as in propagate_eq_infeasible_targetTooLow, but here target={0,20} --
        // gapped, not just narrow -- so neither early bounds-only infeasibility check fires (0<=20,
        // 11>=0). Narrowing target to [9,11] is a valid *numeric* range but deletes both of target's
        // only two actual values (0 and 20), emptying it -- an earlier version of this method recorded
        // that emptied domain via a bare ifPresent(...) without checking isEmpty(), silently letting an
        // empty domain flow into a later propagation round instead of reporting infeasible here.
        Variable<Integer> l = F.create("l_adv_tgap"), r = F.create("r_adv_tgap"), t = F.create("t_adv_tgap");
        var tDomain = NumericDiscreteDomain.of(0, 20);
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.EQ, t)
                .propagate(Map.of(l, IntRangeDomain.of(0, 1), r, IntRangeDomain.of(10, 11), t, tDomain));
        assertThat(result).isEmpty();
    }

    // --- propagate: GEQ (target upper bound only; left/right deliberately untouched) ---

    @Test void propagate_geq_narrowsTargetUpperBound() {
        // L=[0,20], R=[0,5]: dHi=20. T=[0,25], GEQ -> newTHi=min(25,20)=20 (clipped); newTLo
        // stays 0 (LEQ-side narrowing skipped for GEQ).
        var result = of(Operator.GEQ).propagate(domains(0, 20, 0, 5, 0, 25)).orElseThrow();
        assertThat(tDom(result).getMax()).isEqualTo(20.0);
        assertThat(tDom(result).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_geq_doesNotNarrowLeftOrRight() {
        // Same domains as propagate_eq_narrowsLeftMaxUsingTargetBound, but GEQ deliberately skips
        // the conjunctive left/right narrowing that operator gets (see the class's own Javadoc) --
        // confirms L/R are absent from the result even though target itself may narrow.
        var result = of(Operator.GEQ).propagate(domains(0, 20, 0, 5, 0, 3)).orElseThrow();
        assertThat(result.containsKey(L)).isFalse();
        assertThat(result.containsKey(R)).isFalse();
    }

    @Test void propagate_geq_infeasible() {
        // L=[4,6], R=[4,6]: diff range [-2,2] straddles zero -> dHi=2. T=[10,20]: dHi(2) < tLo(10)
        // -> infeasible.
        assertThat(of(Operator.GEQ).propagate(domains(4, 6, 4, 6, 10, 20))).isEmpty();
    }

    @Test void propagate_geq_feasible_noNarrowing() {
        var result = of(Operator.GEQ).propagate(domains(0, 10, 0, 10, 0, 3));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: discrete domains ---

    @Test void propagate_discreteDomains_narrowsTarget() {
        Variable<Integer> l = F.create("l_adv_int"), r = F.create("r_adv_int"), t = F.create("t_adv_int");
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.EQ, t)
                .propagate(Map.of(l, IntRangeDomain.of(0, 10), r, IntRangeDomain.of(0, 4), t, IntRangeDomain.of(0, 20)))
                .orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(t)).toList()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test void propagate_leq_narrowingEmptiesGappedLeftDomain_infeasible() {
        // l={0,10} (gap domain), r=[4,5], target=[0,1] (bound=targetHi=1): l narrows to
        // [max(0,4-1),min(10,5+1)]=[3,6], a non-empty numeric range, but neither 0 nor 10 (l's only
        // two actual values) lies in it -- narrowing empties l even though the fast newLeftLo<=
        // newLeftHi check alone wouldn't have caught this. r is unaffected (unchanged, stays absent
        // from the pruned-check path).
        Variable<Integer> l = F.create("l_adv_gap"), r = F.create("r_adv_gap"), t = F.create("t_adv_gap");
        var lDomain = NumericDiscreteDomain.of(0, 10);
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.LEQ, t)
                .propagate(Map.of(l, lDomain, r, IntRangeDomain.of(4, 5), t, IntRangeDomain.of(0, 1)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_narrowingEmptiesGappedRightDomain_infeasible() {
        // Mirror of the above: r={0,10} (gap domain), l=[4,5], target=[0,1]. l is unchanged (already
        // within the narrowed range), but r narrows to [3,6] and neither 0 nor 10 lies in it --
        // empties r this time, exercising the right-side emptiness check specifically.
        Variable<Integer> l = F.create("l_adv_gap2"), r = F.create("r_adv_gap2"), t = F.create("t_adv_gap2");
        var rDomain = NumericDiscreteDomain.of(0, 10);
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.LEQ, t)
                .propagate(Map.of(l, IntRangeDomain.of(4, 5), r, rDomain, t, IntRangeDomain.of(0, 1)));
        assertThat(result).isEmpty();
    }

    // --- explainInfeasible() / propagateWithReasons() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = of(Operator.EQ).propagateWithReasons(domains(0, 10, 0, 4, 0, 20));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void explainInfeasible_allSingleton_attributesAll() {
        Variable<Integer> l = F.create("l_adv_r1"), r = F.create("r_adv_r1"), t = F.create("t_adv_r1");
        var domainsMap = Map.<Variable<?>, Domain<?>>of(
                l, IntRangeDomain.of(0, 0), r, IntRangeDomain.of(10, 10), t, IntRangeDomain.of(5, 5));
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.EQ, t).propagateWithReasons(domainsMap);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                l, IntervalDomain.of(0, 0), r, IntervalDomain.of(10, 10), t, IntervalDomain.of(5, 5))));
    }

    @Test void explainInfeasible_notAllSingleton_citesCurrentBounds() {
        Variable<Integer> l = F.create("l_adv_r2"), r = F.create("r_adv_r2"), t = F.create("t_adv_r2");
        var domainsMap = Map.<Variable<?>, Domain<?>>of(
                l, IntRangeDomain.of(0, 1), r, IntRangeDomain.of(10, 11), t, IntRangeDomain.of(0, 5));
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.EQ, t).propagateWithReasons(domainsMap);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                l, IntervalDomain.of(0, 1), r, IntervalDomain.of(10, 11), t, IntervalDomain.of(0, 5))));
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_citesExactValueSet() {
        // r's domain {10,12} has a gap at 11 -- RangeNogoodConstraint#fromCurrentBounds' own
        // gaplessness gate declines citing it as a plain range, so this falls through to
        // ValueSetNogoodConstraint, citing every side's exact current value set instead.
        Variable<Integer> l = F.create("l_adv_r3"), r = F.create("r_adv_r3"), t = F.create("t_adv_r3");
        var domainsMap = Map.<Variable<?>, Domain<?>>of(
                l, IntRangeDomain.of(0, 0), r, DiscreteDomain.of(10, 12), t, IntRangeDomain.of(0, 5));
        var result = AbsoluteDifferenceVariableConstraint.of(l, r, Operator.EQ, t).propagateWithReasons(domainsMap);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                l, Set.of(0), r, Set.of(10, 12), t, Set.of(0, 1, 2, 3, 4, 5))));
    }
}
