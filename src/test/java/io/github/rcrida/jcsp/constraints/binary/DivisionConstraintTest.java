package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DivisionConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_div");
    static final Variable<Double> Y = F.create("y_div");

    static Map<Variable<?>, Domain<?>> intervals(double xLo, double xHi, double yLo, double yHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_neq_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.NEQ, 4.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_neq_violated() {
        assertThat(DivisionConstraint.of(X, Y, Operator.NEQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.GEQ, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 8.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(DivisionConstraint.of(X, Y, Operator.GEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.LEQ, 4.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(DivisionConstraint.of(X, Y, Operator.LEQ, 2.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_gt_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.GT, 2.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_gt_violated_atBound() {
        assertThat(DivisionConstraint.of(X, Y, Operator.GT, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_lt_satisfied() {
        assertThat(DivisionConstraint.of(X, Y, Operator.LT, 4.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isTrue();
    }

    @Test void isSatisfiedBy_lt_violated_atBound() {
        assertThat(DivisionConstraint.of(X, Y, Operator.LT, 3.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 6.0, Y, 2.0)))).isFalse();
    }

    @Test void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 1.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 999.0)))).isTrue();
    }

    // --- toString / getRelation ---

    @Test void toString_format() {
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 3.0).toString())
                .isEqualTo("<(x_div, y_div), x_div / y_div == 3.0>");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var a = DivisionConstraint.<Double>builder().left(X).right(Y).operator(Operator.GEQ).bound(2.0).build();
        assertThat(DivisionConstraint.of(X, Y, Operator.GEQ, 2.0)).isEqualTo(a);
    }

    // --- propagate: non-propagating operators ---

    @Test void propagate_neq_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.NEQ, 3.0).propagate(intervals(1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_lt_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.LT, 3.0).propagate(intervals(1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_gt_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.GT, 3.0).propagate(intervals(1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: non-positive bound ---

    @Test void propagate_geq_negativeBound_triviallyTrueSkipsNarrowing() {
        // x/y ≥ -2 with x,y > 0 is always true; no narrowing should occur
        var result = DivisionConstraint.of(X, Y, Operator.GEQ, -2.0).propagate(intervals(1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_geq_zeroBound_triviallyTrueSkipsNarrowing() {
        // x/y ≥ 0 with x,y > 0 is always true; no narrowing should occur
        var result = DivisionConstraint.of(X, Y, Operator.GEQ, 0.0).propagate(intervals(1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: non-positive domain ---

    @Test void propagate_zeroDividendMin_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.GEQ, 2.0).propagate(intervals(0, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_negativeDividendMin_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.LEQ, 2.0).propagate(intervals(-1, 10, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_zeroDivisorMin_returnsEmptyMap() {
        var result = DivisionConstraint.of(X, Y, Operator.EQ, 2.0).propagate(intervals(1, 10, 0, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: infeasibility ---

    @Test void propagate_leq_infeasible_minRatioAboveBound() {
        // X∈[6,10], Y∈[1,2], xMin/yMax = 6/2 = 3.0 > 2.0 → infeasible
        assertThat(DivisionConstraint.of(X, Y, Operator.LEQ, 2.0).propagate(intervals(6, 10, 1, 2))).isEmpty();
    }

    @Test void propagate_geq_infeasible_maxRatioBelowBound() {
        // X∈[1,4], Y∈[2,5], xMax/yMin = 4/2 = 2.0 < 3.0 → infeasible
        assertThat(DivisionConstraint.of(X, Y, Operator.GEQ, 3.0).propagate(intervals(1, 4, 2, 5))).isEmpty();
    }

    @Test void propagate_eq_infeasible_ratioAboveRange() {
        // X∈[1,4], Y∈[2,5], xMax/yMin = 2.0 < 3.0 → infeasible (GEQ check)
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 3.0).propagate(intervals(1, 4, 2, 5))).isEmpty();
    }

    @Test void propagate_eq_infeasible_ratioBelowRange() {
        // X∈[6,10], Y∈[1,2], xMin/yMax = 3.0 > 2.0 → infeasible (LEQ check)
        assertThat(DivisionConstraint.of(X, Y, Operator.EQ, 2.0).propagate(intervals(6, 10, 1, 2))).isEmpty();
    }

    // --- propagate: GEQ (raises x.min, clips y.max) ---

    @Test void propagate_geq_raisesXMin() {
        // X∈[1,10], Y∈[1,3], k=4 (GEQ)
        // newXMin = k*yMin = 4*1 = 4 > 1 → raise x.min to 4
        // newYMax = xMax/k = 10/4 = 2.5 < 3 → clip y.max to 2.5
        var result = DivisionConstraint.of(X, Y, Operator.GEQ, 4.0).propagate(intervals(1, 10, 1, 3)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(4.0);
        assertThat(xDom(result).getMax()).isEqualTo(10.0);
        assertThat(yDom(result).getMin()).isEqualTo(1.0);
        assertThat(yDom(result).getMax()).isEqualTo(2.5);
    }

    @Test void propagate_geq_noChangeWhenAlreadySatisfied() {
        // X∈[4,10], Y∈[1,2], k=2 (GEQ)
        // newXMin = 2*1 = 2 ≤ 4 → no change; newYMax = 10/2 = 5 ≥ 2 → no change
        var result = DivisionConstraint.of(X, Y, Operator.GEQ, 2.0).propagate(intervals(4, 10, 1, 2)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: LEQ (clips x.max, raises y.min) ---

    @Test void propagate_leq_clipsXMaxAndRaisesYMin() {
        // X∈[1,10], Y∈[1,5], k=2 (LEQ)
        // newXMax = k*yMax = 2*5 = 10 — no change
        // newYMin = xMin/k = 1/2 = 0.5 ≤ 1 — no change
        // Actually let's use different numbers: X∈[2,10], Y∈[1,3], k=2 (LEQ)
        // newXMax = 2*3 = 6 < 10 → clip x.max to 6
        // newYMin = 2/2 = 1.0 — no change
        var result = DivisionConstraint.of(X, Y, Operator.LEQ, 2.0).propagate(intervals(2, 10, 1, 3)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(6.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_leq_raisesYMin() {
        // X∈[4,10], Y∈[1,5], k=2 (LEQ)
        // newXMax = 2*5 = 10 — no change
        // newYMin = 4/2 = 2.0 > 1 → raise y.min to 2.0
        var result = DivisionConstraint.of(X, Y, Operator.LEQ, 2.0).propagate(intervals(4, 10, 1, 5)).orElseThrow();
        assertThat(result).doesNotContainKey(X);
        assertThat(yDom(result).getMin()).isEqualTo(2.0);
        assertThat(yDom(result).getMax()).isEqualTo(5.0);
    }

    @Test void propagate_leq_noChangeWhenAlreadyTight() {
        // X∈[1,4], Y∈[2,5], k=3 (LEQ)
        // newXMax = 3*5 = 15 ≥ 4 — no change; newYMin = 1/3 ≈ 0.33 ≤ 2 — no change
        var result = DivisionConstraint.of(X, Y, Operator.LEQ, 3.0).propagate(intervals(1, 4, 2, 5)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: EQ (both passes) ---

    @Test void propagate_eq_tightensBothSides() {
        // X∈[1,12], Y∈[1,4], k=3 (EQ)
        // LEQ pass: newXMax = 3*4 = 12 — no change; newYMin = 1/3 ≈ 0.33 ≤ 1 — no change
        // GEQ pass: newXMin = 3*1 = 3 > 1 → raise x.min to 3; newYMax = 12/3 = 4 — no change
        var result = DivisionConstraint.of(X, Y, Operator.EQ, 3.0).propagate(intervals(1, 12, 1, 4)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(3.0);
        assertThat(xDom(result).getMax()).isEqualTo(12.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_eq_tightensYMax() {
        // X∈[3,9], Y∈[1,6], k=3 (EQ)
        // LEQ: newXMax=3*6=18>=9 no change; newYMin=3/3=1 no change
        // GEQ: newXMin=3*1=3 no change; newYMax=9/3=3<6 → clip y.max to 3
        var result = DivisionConstraint.of(X, Y, Operator.EQ, 3.0).propagate(intervals(3, 9, 1, 6)).orElseThrow();
        assertThat(result).doesNotContainKey(X);
        assertThat(yDom(result).getMax()).isEqualTo(3.0);
    }

    // --- propagate: discrete domain ---

    @Test void propagate_geq_discreteDomain_raisesXMin() {
        Variable<Integer> a = F.create("a_dv"), b = F.create("b_dv");
        // a∈[1,10], b∈[1,2], k=4 (GEQ): newAMin=4*1=4 → values 1,2,3 removed
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1, 10), b, IntRangeDomain.of(1, 2));
        var result = DivisionConstraint.of(a, b, Operator.GEQ, 4).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(3)).isFalse();
        assertThat(result.get(a).contains(4)).isTrue();
    }

    @Test void propagate_eq_discreteDomain_infeasible_noExactRatio() {
        // X={5}, Y={3}, k=2 (EQ): xMin/yMax=5/3≈1.67<2 passes LEQ; xMax/yMin=5/3<2 fails GEQ → infeasible
        Variable<Integer> a = F.create("a_dv2"), b = F.create("b_dv2");
        var domA = DomainObjectSet.<Integer>builder().value(5).build();
        var domB = DomainObjectSet.<Integer>builder().value(3).build();
        assertThat(DivisionConstraint.of(a, b, Operator.EQ, 2).propagate(Map.of(a, domA, b, domB))).isEmpty();
    }

    @Test void propagate_eq_discreteDomain_infeasible_leqClipsXMaxBelowGeqXMin() {
        // X={2,10}, Y={1,3}, k=3 (EQ)
        // LEQ: newXMax=3*3=9 < 10 → X clips to {2}, xMax=2
        // GEQ: newXMin=3*1=3 > 2=xMin → narrow X to [3,2] → empty → infeasible
        Variable<Integer> a = F.create("a_dv3"), b = F.create("b_dv3");
        var domA = DomainObjectSet.<Integer>builder().value(2).value(10).build();
        var domB = DomainObjectSet.<Integer>builder().value(1).value(3).build();
        assertThat(DivisionConstraint.of(a, b, Operator.EQ, 3).propagate(Map.of(a, domA, b, domB))).isEmpty();
    }

    // --- solver integration ---

    @Test void solver_quotientPairsOf3() {
        // Find all integer pairs (x,y) where x/y==3.0, x∈[1,12], y∈[1,4]
        Variable<Integer> x = F.create("dx"), y = F.create("dy");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 12))
                .variableDomain(y, IntRangeDomain.of(1, 4))
                .divisionConstraint(x, y, Operator.EQ, 3)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        // (3,1),(6,2),(9,3),(12,4)
        assertThat(solutions).hasSize(4);
        assertThat(solutions).allMatch(a ->
                a.getValue(x).orElseThrow().doubleValue() / a.getValue(y).orElseThrow().doubleValue() == 3.0);
    }
}
