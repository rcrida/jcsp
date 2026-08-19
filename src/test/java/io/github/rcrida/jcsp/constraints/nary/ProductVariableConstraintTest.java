package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_prodv");
    static final Variable<Double> Y = F.create("y_prodv");
    static final Variable<Double> T = F.create("t_prodv");

    static Map<Variable<?>, Domain<?>> intervals(double xLo, double xHi, double yLo, double yHi, double tLo, double tHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi), T, IntervalDomain.of(tLo, tHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }
    static IntervalDomain tDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(T); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(ProductVariableConstraint.of(Set.of(X, Y), Operator.EQ, T)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0, T, 6.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(ProductVariableConstraint.of(Set.of(X, Y), Operator.EQ, T)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0, T, 5.0)))).isFalse();
    }

    @Test void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        assertThat(ProductVariableConstraint.of(Set.of(X, Y), Operator.EQ, T)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0)))).isTrue();
    }

    // --- propagate: non-positive domain ---

    @Test void propagate_zeroFactorMin_returnsEmptyMap() {
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.GEQ, T).propagate(intervals(0, 5, 1, 3, 0, 20));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: non-propagating operators ---

    @Test void propagate_neq_returnsEmptyMap() {
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.NEQ, T).propagate(intervals(1, 5, 1, 3, 0, 20));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: infeasibility ---

    @Test void propagate_infeasible_productMinAboveTargetMax() {
        // X∈[2,4], Y∈[2,4], productMin=4, target∈[0,1] → 4 > 1, LEQ-like infeasible
        assertThat(ProductVariableConstraint.of(Set.of(X, Y), Operator.LEQ, T).propagate(intervals(2, 4, 2, 4, 0, 1))).isEmpty();
    }

    @Test void propagate_infeasible_productMaxBelowTargetMin() {
        // X∈[1,2], Y∈[1,2], productMax=4, target∈[5,10] → 4 < 5, GEQ-like infeasible
        assertThat(ProductVariableConstraint.of(Set.of(X, Y), Operator.GEQ, T).propagate(intervals(1, 2, 1, 2, 5, 10))).isEmpty();
    }

    // --- propagate: GEQ (lower-bound pass on factors, upper-bound pass on target) ---

    @Test void propagate_geq_raisesOneFactorMin() {
        // X∈[1,10], Y∈[1,2], target∈[8,8] (GEQ): productMax=20
        // newMin[X]=8*10/20=4>1→raise; newMin[Y]=8*2/20=0.8<1→no change
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.GEQ, T).propagate(intervals(1, 10, 1, 2, 8, 8)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(4.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_geq_lowersTargetMax() {
        // X∈[1,2], Y∈[1,2], target∈[0,10] (GEQ): productMax=4 < target's max 10 → target's max falls to 4
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.GEQ, T).propagate(intervals(1, 2, 1, 2, 0, 10)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(0.0);
        assertThat(tDom(result).getMax()).isEqualTo(4.0);
    }

    // --- propagate: LEQ (upper-bound pass on factors, lower-bound pass on target) ---

    @Test void propagate_leq_lowersBothFactorMaxes() {
        // X∈[1,10], Y∈[1,10], target∈[6,6] (LEQ): productMin=1
        // newMax[X]=6*1/1=6<10→clip; newMax[Y]=6<10→clip
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.LEQ, T).propagate(intervals(1, 10, 1, 10, 6, 6)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(6.0);
        assertThat(yDom(result).getMax()).isEqualTo(6.0);
    }

    @Test void propagate_leq_raisesTargetMin() {
        // X∈[3,5], Y∈[3,5], target∈[0,100] (LEQ): productMin=9 > target's min 0 → target's min rises to 9
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.LEQ, T).propagate(intervals(3, 5, 3, 5, 0, 100)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(9.0);
        assertThat(tDom(result).getMax()).isEqualTo(100.0);
    }

    // --- propagate: EQ (both passes, both sides) ---

    @Test void propagate_eq_tightensFactorsAndTarget() {
        // X∈[1,10], Y∈[1,3], target∈[0,100] (EQ): productMin=1, productMax=30
        // Target narrows to [1,30]; using tHi=30 for the LEQ pass doesn't clip X/Y here since
        // 30*1/1=30>=10 -- pick a tighter target to force factor narrowing too.
        var result = ProductVariableConstraint.of(Set.of(X, Y), Operator.EQ, T).propagate(intervals(1, 10, 1, 3, 0, 6)).orElseThrow();
        // target's max stays 6 (already tighter than productMax=30); target's min rises to productMin=1
        assertThat(tDom(result).getMin()).isEqualTo(1.0);
        // LEQ pass: newMax[X] = 6*1/1 = 6 < 10 -> clipped
        assertThat(xDom(result).getMax()).isEqualTo(6.0);
    }

    // --- propagate: gappy target domain (the real lesson from GlobalCardinalityVariableConstraint) ---

    @Test void propagate_infeasible_narrowedTargetRangeFallsInGap() {
        // a definite-ish factors: a=3, b=3 -> product is pinned to 9 exactly (productMin=productMax=9).
        // target's domain is {5,20} (gappy): narrowing target to [9,9] falls entirely in the gap --
        // a non-inverted range that still contains no actual present value.
        Variable<Integer> a = F.create("a_prodv_gap"), b = F.create("b_prodv_gap"), t = F.create("t_prodv_gap");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, IntRangeDomain.of(3, 3), b, IntRangeDomain.of(3, 3), t, DiscreteDomain.of(5, 20));
        assertThat(ProductVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    @Test void propagate_eq_discreteDomain_infeasible_factorLowerBoundNarrowingFindsGap() {
        // Mirrors ProductConstraint#propagate_eq_discreteDomain_infeasible_noProductEqualsK, with a
        // variable target instead of a fixed bound: a={2,5}, b={1}, target={4} (EQ). productMin=2,
        // productMax=5, target=4 is in [2,5] so the early feasibility check passes. LEQ pass clips
        // a from {2,5} to {2} (newMax=4*1/1=4, keeping only values <=4 -> just 2). GEQ pass then
        // raises a's (already-clipped-to-{2}) domain to >=4 -- {2} has nothing >=4 -> empty.
        Variable<Integer> a = F.create("a_prodv_eqgap"), b = F.create("b_prodv_eqgap"), t = F.create("t_prodv_eqgap");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 5), b, DiscreteDomain.of(1), t, DiscreteDomain.of(4));
        assertThat(ProductVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_infeasible_citesCurrentBounds() {
        var c = ProductVariableConstraint.of(Set.of(X, Y), Operator.GEQ, T);
        var domains = intervals(1, 2, 1, 2, 5, 10);
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isPresent();
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_fallsThroughToGroundReason() {
        // a's domain {2,5} is gapped and non-singleton -- RangeNogoodConstraint.fromCurrentBounds
        // can't soundly cite it as a range; allSingletonReason's ground fallback also can't since
        // it isn't singleton either -- the overall result is empty, same as ProductConstraint's
        // own documented behaviour for this exact shape.
        Variable<Integer> a = F.create("a_prodv_gapreason"), b = F.create("b_prodv_gapreason"), t = F.create("t_prodv_gapreason");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 5), b, DiscreteDomain.of(1), t, DiscreteDomain.of(100));
        var c = ProductVariableConstraint.of(Set.of(a, b), Operator.GEQ, t);
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- getRelation ---

    @Test void getRelation_containsFactorsAndTarget() {
        var relation = ProductVariableConstraint.of(Set.of(X, Y), Operator.EQ, T).getRelation();
        assertThat(relation).contains("*").contains(T.toString());
    }

    // --- ConstraintSatisfactionProblem builder wiring ---

    @Test void cspBuilder_productConstraint_variableOverload() {
        Variable<Integer> a = F.create("a_prodv_csp"), b = F.create("b_prodv_csp"), t = F.create("t_prodv_csp");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 4))
                .variableDomain(b, IntRangeDomain.of(1, 4))
                .variableDomain(t, IntRangeDomain.of(0, 16))
                .productConstraint(Set.of(a, b), Operator.EQ, t)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).isNotEmpty();
        for (var s : solutions) {
            int av = s.getValue(a).orElseThrow();
            int bv = s.getValue(b).orElseThrow();
            int tv = s.getValue(t).orElseThrow();
            assertThat(av * bv).isEqualTo(tv);
        }
    }
}
