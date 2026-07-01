package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MaxConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_max");
    static final Variable<Double> Y = F.create("y_max");
    static final Variable<Double> Z = F.create("z_max");

    static Map<Variable<?>, Domain<?>> intervals(double xLo, double xHi, double yLo, double yHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi));
    }

    static Map<Variable<?>, Domain<?>> threeIntervals(double xLo, double xHi,
                                                       double yLo, double yHi,
                                                       double zLo, double zHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi),
                      Y, IntervalDomain.of(yLo, yHi),
                      Z, IntervalDomain.of(zLo, zHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }
    static IntervalDomain zDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Z); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.EQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.EQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 6.0)))).isFalse();
    }

    @Test void isSatisfiedBy_neq_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.NEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 6.0)))).isTrue();
    }

    @Test void isSatisfiedBy_neq_violated() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.NEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 7.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 8.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_lt_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LT, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 6.0)))).isTrue();
    }

    @Test void isSatisfiedBy_lt_violated_atBound() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LT, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 5.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 4.0)))).isFalse();
    }

    @Test void isSatisfiedBy_gt_satisfied() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_gt_violated_atBound() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 4.0)))).isFalse();
    }

    @Test void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        // Only one of two variables assigned → optimistically satisfied regardless of value
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 10.0)))).isTrue();
    }

    // --- toString / getRelation ---

    @Test void toString_format() {
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).toString())
                .isEqualTo("<(x_max, y_max), max(x_max, y_max) <= 5.0>");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var a = MaxConstraint.<Double>builder().variable(X).variable(Y).operator(Operator.LEQ).bound(5.0).build();
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0)).isEqualTo(a);
    }

    // --- propagate: NEQ skipped ---

    @Test void propagate_neq_returnsEmptyMap() {
        var result = MaxConstraint.of(Set.of(X, Y), Operator.NEQ, 5.0).propagate(intervals(0, 10, 0, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: LEQ ---

    @Test void propagate_leq_clipsMaxOfBothVariables() {
        // X∈[0,10], Y∈[0,8], max<=5: both maxes clip to 5
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 10, 0, 8)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(yDom(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_leq_noChangeWhenAlreadyWithinBound() {
        // X∈[0,3], Y∈[0,4], max<=5: both already <= 5, no update
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 3, 0, 4)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_partialClip_onlyOneVariableChanges() {
        // X∈[0,3], Y∈[0,10], max<=5: only Y clips
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 3, 0, 10)).orElseThrow();
        assertThat(result).containsKey(Y);
        assertThat(result).doesNotContainKey(X);
        assertThat(yDom(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_leq_infeasible_globalMinExceedsBound() {
        // X∈[6,10], Y∈[7,9], max<=5: globalMin = max(6,7)=7 > 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(6, 10, 7, 9))).isEmpty();
    }

    // --- propagate: LT (strict) ---

    @Test void propagate_lt_clipsMax() {
        // X∈[0,10], Y∈[0,8], max<5: both clip to 5
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(0, 10, 0, 8)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_lt_infeasible_globalMinAtBound() {
        // X∈[5,10], Y∈[0,3], max<5: globalMin = max(5,0)=5 >= 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(5, 10, 0, 3))).isEmpty();
    }

    // --- propagate: GEQ ---

    @Test void propagate_geq_infeasible_noVariableCanReachBound() {
        // X∈[0,3], Y∈[0,4], max>=5: globalMax=4 < 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(0, 3, 0, 4))).isEmpty();
    }

    @Test void propagate_geq_forcesMinUpWhenOnlyOneReaches() {
        // X∈[0,10], Y∈[0,3], max>=5: only X can reach 5; X.min raised to 5
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(0, 10, 0, 3)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_geq_noForcingWhenMultipleCanReach() {
        // X∈[0,10], Y∈[0,10], max>=5: both can reach; no narrowing
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_geq_noForcingWhenMinAlreadyAtBound() {
        // X∈[5,10], Y∈[0,3], max>=5: only X reaches, but X.min=5 already >= 5 → no update
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(5, 10, 0, 3)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: GT (strict) ---

    @Test void propagate_gt_infeasible_globalMaxAtBound() {
        // X∈[0,5], Y∈[0,5], max>5: globalMax=5 <= 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 5, 0, 5))).isEmpty();
    }

    @Test void propagate_gt_infeasible_globalMaxBelowBound() {
        // X∈[0,4], Y∈[0,3], max>5: globalMax=4 < 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 4, 0, 3))).isEmpty();
    }

    @Test void propagate_gt_forcesMinUpWhenOnlyOneExceedsBound() {
        // X∈[0,10], Y∈[0,4], max>5: only X has max>5; X.min raised to 5
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 10, 0, 4)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
    }

    @Test void propagate_gt_feasible_noNarrowing() {
        // X∈[0,10], Y∈[0,10], max>5: both exceed 5, no forcing
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_clipsMaxAndForcesMin_oneSurvivor() {
        // X∈[0,10], Y∈[0,3], max==5: clip both → X∈[0,5], Y∈[0,3]; only X can reach 5 → X=[5,5]
        var result = MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 10, 0, 3)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_eq_clipsMaxOnly_twoSurvivors() {
        // X∈[0,10], Y∈[0,10], max==5: both clip to 5; both can still reach 5 → no forced narrowing
        var result = MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(yDom(result).getMax()).isEqualTo(5.0);
        assertThat(xDom(result).getMin()).isEqualTo(0.0);
        assertThat(yDom(result).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_eq_infeasible_globalMinExceedsBound() {
        // X∈[6,10], Y∈[7,9], max==5: globalMin=7 > 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(6, 10, 7, 9))).isEmpty();
    }

    @Test void propagate_eq_infeasible_noVariableReachesBound() {
        // X∈[0,3], Y∈[0,4], max==5: after no upper-clip change; globalMax=4 < 5 → infeasible
        assertThat(MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 3, 0, 4))).isEmpty();
    }

    @Test void propagate_eq_threeVariables_oneSurvivorForcedToSingleton() {
        // X∈[0,10], Y∈[0,3], Z∈[0,2], max==5: clip → X∈[0,5]; only X can reach 5 → X=[5,5]
        var domains = threeIntervals(0, 10, 0, 3, 0, 2);
        var result = MaxConstraint.of(Set.of(X, Y, Z), Operator.EQ, 5.0).propagate(domains).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
        assertThat(result).doesNotContainKey(Z);
    }

    // --- propagate: discrete domain (IntRangeDomain) ---

    @Test void propagate_leq_discreteDomain_clipsValues() {
        Variable<Integer> a = F.create("a_mx"), b = F.create("b_mx");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 10), b, IntRangeDomain.of(0, 5));
        var result = MaxConstraint.of(Set.of(a, b), Operator.LEQ, 6).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(6)).isTrue();
        assertThat(result.get(a).contains(7)).isFalse();
        assertThat(result).doesNotContainKey(b);
    }

    @Test void propagate_geq_discreteDomain_forcesMinUp() {
        Variable<Integer> a = F.create("a_mx2"), b = F.create("b_mx2");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 8), b, IntRangeDomain.of(0, 4));
        // only a can reach 5 (a.max=8>=5, b.max=4<5)
        var result = MaxConstraint.of(Set.of(a, b), Operator.GEQ, 5).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(5)).isTrue();
        assertThat(result.get(a).contains(4)).isFalse();
        assertThat(result).doesNotContainKey(b);
    }

    @Test void propagate_eq_discreteDomain_infeasible_noValueEqualsK() {
        // Discrete domain {0,1,2,4} (gap at 3), bound domain {0,1,2}, max==3:
        // upper-clip: a clips to {0,1,2}, maxs[a] set to 3; b already <= 3 (unchanged)
        // lower-bound: only a has tracked max=3 (from clip); forced narrow({0,1,2}, 3, 3) → empty → infeasible
        Variable<Integer> a = F.create("a_mx3"), b = F.create("b_mx3");
        var domA = DomainObjectSet.<Integer>builder().value(0).value(1).value(2).value(4).build();
        var domB = IntRangeDomain.of(0, 2);
        var domains = Map.<Variable<?>, Domain<?>>of(a, domA, b, domB);
        assertThat(MaxConstraint.of(Set.of(a, b), Operator.EQ, 3).propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagateWithReasons(intervals(0, 10, 0, 8));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_leq_singleCulprit_attributesSingleton() {
        // X=[7,7] (singleton, exceeds 5), Y=[0,3]: max<=5 infeasible; X alone is a sufficient
        // culprit regardless of Y.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagateWithReasons(intervals(7, 7, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsExactly(Map.entry(X, 7.0));
    }

    @Test void propagateWithReasons_leq_noSingletonCulprit_bothOpen_returnsEmptyReason() {
        // X=[6,10], Y=[7,9]: max<=5 infeasible (globalMin=7>5), but neither side is pinned to a
        // single value, so nothing can be blamed.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagateWithReasons(intervals(6, 10, 7, 9));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_leq_singletonDoesNotExceed_openVariableIsTrueCulprit_returnsEmptyReason() {
        // X=[3,3] (singleton, does not exceed 5), Y=[6,10] (open, the real culprit): infeasible,
        // but Y isn't pinned to a value so nothing can be blamed.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagateWithReasons(intervals(3, 3, 6, 10));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_lt_singleCulprit_strictAttributesSingleton() {
        // X=[5,5] (singleton, at bound), Y=[0,3]: max<5 infeasible (globalMin=5>=5); X's value
        // pinned exactly at the bound already violates the strict comparison.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagateWithReasons(intervals(5, 5, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsExactly(Map.entry(X, 5.0));
    }

    @Test void propagateWithReasons_lt_singletonDoesNotExceedStrictly_openVariableIsTrueCulprit_returnsEmptyReason() {
        // X=[3,3] (singleton, doesn't reach the strict bound), Y=[5,10] (open, the real culprit
        // under the strict comparison): infeasible, but Y isn't pinned so nothing can be blamed.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagateWithReasons(intervals(3, 3, 5, 10));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_geq_collective_allSingleton_attributesBoth() {
        // X=[3,3], Y=[4,4]: max>=5 infeasible (globalMax=4<5); neither individually reaches, but
        // both are pinned, so the full pair is a sound, self-contained explanation.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagateWithReasons(intervals(3, 3, 4, 4));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(X, 3.0), Map.entry(Y, 4.0));
    }

    @Test void propagateWithReasons_geq_collective_notAllSingleton_returnsEmptyReason() {
        // X=[0,3], Y=[0,4]: max>=5 infeasible (globalMax=4<5), but neither is pinned, so an
        // unlisted open variable can't be ruled out — falls back to empty.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagateWithReasons(intervals(0, 3, 0, 4));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_gt_collective_allSingleton_attributesBoth() {
        // X=[3,3], Y=[3,3]: max>5 infeasible (globalMax=3<=5); both pinned, sound explanation.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagateWithReasons(intervals(3, 3, 3, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(X, 3.0), Map.entry(Y, 3.0));
    }

    @Test void propagateWithReasons_eq_singleCulpritFoundBeforeCollective() {
        // X=[8,8] (singleton, exceeds 5), Y=[0,3], max==5: infeasible via the upper-bound check;
        // the single-culprit search finds X before ever considering the collective explanation.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagateWithReasons(intervals(8, 8, 0, 3));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsExactly(Map.entry(X, 8.0));
    }

    @Test void propagateWithReasons_eq_collectiveFoundAfterSingleCulpritMisses() {
        // X=[3,3], Y=[4,4], max==5: upper-bound check passes (neither exceeds 5), but the
        // collective lower-bound explanation succeeds since both are pinned and neither reaches 5.
        var result = MaxConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagateWithReasons(intervals(3, 3, 4, 4));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(X, 3.0), Map.entry(Y, 4.0));
    }
}
