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

public class MinConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_min");
    static final Variable<Double> Y = F.create("y_min");
    static final Variable<Double> Z = F.create("z_min");

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
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.EQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 7.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.EQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 4.0, Y, 6.0)))).isFalse();
    }

    @Test void isSatisfiedBy_neq_satisfied() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.NEQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 4.0, Y, 6.0)))).isTrue();
    }

    @Test void isSatisfiedBy_neq_violated() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.NEQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 7.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 8.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 4.0, Y, 9.0)))).isFalse();
    }

    @Test void isSatisfiedBy_gt_satisfied() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GT, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 9.0)))).isTrue();
    }

    @Test void isSatisfiedBy_gt_violated_atBound() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GT, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 8.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 10.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LEQ, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 8.0, Y, 9.0)))).isFalse();
    }

    @Test void isSatisfiedBy_lt_satisfied() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LT, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 9.0)))).isTrue();
    }

    @Test void isSatisfiedBy_lt_violated_atBound() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LT, 7.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 9.0)))).isFalse();
    }

    @Test void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 10.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 1.0)))).isTrue();
    }

    // --- toString / getRelation ---

    @Test void toString_format() {
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).toString())
                .isEqualTo("<(x_min, y_min), min(x_min, y_min) >= 5.0>");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var a = MinConstraint.<Double>builder().variable(X).variable(Y).operator(Operator.GEQ).bound(5.0).build();
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)).isEqualTo(a);
    }

    // --- propagate: NEQ skipped ---

    @Test void propagate_neq_returnsEmptyMap() {
        var result = MinConstraint.of(Set.of(X, Y), Operator.NEQ, 5.0).propagate(intervals(0, 10, 0, 10));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: GEQ ---

    @Test void propagate_geq_raisesMinOfBothVariables() {
        // X∈[0,10], Y∈[2,8], min>=5: both mins raise to 5
        var result = MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(0, 10, 2, 8)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(yDom(result).getMin()).isEqualTo(5.0);
    }

    @Test void propagate_geq_noChangeWhenAlreadyWithinBound() {
        // X∈[5,10], Y∈[7,8], min>=5: both already >= 5, no update
        var result = MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(5, 10, 7, 8)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_geq_partialRaise_onlyOneVariableChanges() {
        // X∈[7,10], Y∈[0,8], min>=5: only Y min raises
        var result = MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(7, 10, 0, 8)).orElseThrow();
        assertThat(result).containsKey(Y);
        assertThat(result).doesNotContainKey(X);
        assertThat(yDom(result).getMin()).isEqualTo(5.0);
    }

    @Test void propagate_geq_infeasible_globalSmallestMaxBelowBound() {
        // X∈[0,4], Y∈[0,3], min>=5: smallest max=3 < 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(0, 4, 0, 3))).isEmpty();
    }

    // --- propagate: GT (strict) ---

    @Test void propagate_gt_raisesMin() {
        // X∈[0,10], Y∈[0,8], min>5: both mins raise to 5
        var result = MinConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 10, 0, 8)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
    }

    @Test void propagate_gt_infeasible_globalSmallestMaxAtBound() {
        // X∈[0,5], Y∈[0,8], min>5: smallest max=5 <= 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.GT, 5.0).propagate(intervals(0, 5, 0, 8))).isEmpty();
    }

    // --- propagate: LEQ ---

    @Test void propagate_leq_infeasible_noVariableCanReachBound() {
        // X∈[6,10], Y∈[7,9], min<=5: smallest min=6 > 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(6, 10, 7, 9))).isEmpty();
    }

    @Test void propagate_leq_forcesMaxDownWhenOnlyOneReaches() {
        // X∈[0,10], Y∈[6,10], min<=5: only X can reach ≤5; X.max clipped to 5
        var result = MinConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 10, 6, 10)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_leq_noForcingWhenMultipleCanReach() {
        // X∈[0,10], Y∈[0,10], min<=5: both can reach ≤5; no narrowing
        var result = MinConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_noForcingWhenMaxAlreadyAtBound() {
        // X∈[0,5], Y∈[6,10], min<=5: only X reaches; X.max=5 already <= 5 → no update
        var result = MinConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0).propagate(intervals(0, 5, 6, 10)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: LT (strict) ---

    @Test void propagate_lt_infeasible_globalSmallestMinAtBound() {
        // X∈[5,10], Y∈[6,9], min<5: smallest min=5 >= 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(5, 10, 6, 9))).isEmpty();
    }

    @Test void propagate_lt_infeasible_globalSmallestMinAboveBound() {
        // X∈[6,10], Y∈[7,9], min<5: smallest min=6 > 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(6, 10, 7, 9))).isEmpty();
    }

    @Test void propagate_lt_forcesMaxDownWhenOnlyOneReachesBelow() {
        // X∈[0,10], Y∈[7,10], min<5: only X has min<5; X.max clipped to 5
        var result = MinConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(0, 10, 7, 10)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_lt_feasible_noNarrowing() {
        // X∈[0,10], Y∈[0,10], min<5: both have min<5, no forcing
        var result = MinConstraint.of(Set.of(X, Y), Operator.LT, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_raisesMinAndForcesMax_oneSurvivor() {
        // X∈[0,10], Y∈[6,10], min==5: raise both → X∈[5,10]; only X has min=5 → X=[5,5]
        var result = MinConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 10, 6, 10)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_eq_raisesMinOnly_twoSurvivors() {
        // X∈[0,10], Y∈[0,10], min==5: raise both mins to 5; both can still reach 5 → no forced max-clip
        var result = MinConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 10, 0, 10)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(yDom(result).getMin()).isEqualTo(5.0);
        assertThat(xDom(result).getMax()).isEqualTo(10.0);
        assertThat(yDom(result).getMax()).isEqualTo(10.0);
    }

    @Test void propagate_eq_infeasible_globalSmallestMaxBelowBound() {
        // X∈[0,4], Y∈[0,3], min==5: smallest max=3 < 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(0, 4, 0, 3))).isEmpty();
    }

    @Test void propagate_eq_infeasible_noVariableReachesBound() {
        // X∈[7,10], Y∈[8,10], min==5: lower pass raises nothing (all mins > 5... wait mins are 7,8 both > 5)
        // Actually: lower pass checks globalSmallestMax=10 >= 5, OK; mins[i] < 5 → false for both → no raise
        // Upper pass: globalSmallestMin=min(7,8)=7 > 5 → infeasible
        assertThat(MinConstraint.of(Set.of(X, Y), Operator.EQ, 5.0).propagate(intervals(7, 10, 8, 10))).isEmpty();
    }

    @Test void propagate_eq_threeVariables_oneSurvivorForcedToSingleton() {
        // X∈[0,10], Y∈[6,10], Z∈[7,10], min==5: raise X→[5,10]; only X has min=5 → X=[5,5]
        var domains = threeIntervals(0, 10, 6, 10, 7, 10);
        var result = MinConstraint.of(Set.of(X, Y, Z), Operator.EQ, 5.0).propagate(domains).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
        assertThat(result).doesNotContainKey(Z);
    }

    // --- propagate: discrete domain (IntRangeDomain) ---

    @Test void propagate_geq_discreteDomain_raisesValues() {
        Variable<Integer> a = F.create("a_mn"), b = F.create("b_mn");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 10), b, IntRangeDomain.of(0, 5));
        var result = MinConstraint.of(Set.of(a, b), Operator.GEQ, 3).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(2)).isFalse();
        assertThat(result.get(a).contains(3)).isTrue();
        assertThat(result.get(b).contains(2)).isFalse();
        assertThat(result.get(b).contains(3)).isTrue();
    }

    @Test void propagate_leq_discreteDomain_forcesMaxDown() {
        Variable<Integer> a = F.create("a_mn2"), b = F.create("b_mn2");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 8), b, IntRangeDomain.of(6, 10));
        // only a can reach ≤5 (a.min=0<=5, b.min=6>5); a.max clips to 5
        var result = MinConstraint.of(Set.of(a, b), Operator.LEQ, 5).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(5)).isTrue();
        assertThat(result.get(a).contains(6)).isFalse();
        assertThat(result).doesNotContainKey(b);
    }

    @Test void propagate_eq_discreteDomain_infeasible_noValueEqualsK() {
        // Domain a={2,4} (gap at 3), domain b={4,5}: min==3
        // Lower pass: raise a's min to 3 → a becomes {4}; b unchanged
        // Upper pass: only a has min(tracked)=3 ≤ 3; clip a's max (4) down to 3 → narrow({4},3,3)=∅ → infeasible
        Variable<Integer> a = F.create("a_mn3"), b = F.create("b_mn3");
        var domA = DomainObjectSet.<Integer>builder().value(2).value(4).build();
        var domB = IntRangeDomain.of(4, 5);
        var domains = Map.<Variable<?>, Domain<?>>of(a, domA, b, domB);
        assertThat(MinConstraint.of(Set.of(a, b), Operator.EQ, 3).propagate(domains)).isEmpty();
    }
}
