package io.github.rcrida.jcsp.constraints.nary;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_prod");
    static final Variable<Double> Y = F.create("y_prod");

    static Map<Variable<?>, Domain<?>> intervals(double xLo, double xHi, double yLo, double yHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 6.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_neq_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.NEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_neq_violated() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.NEQ, 6.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 6.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 4.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 10.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 10.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_gt_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GT, 5.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_gt_violated_atBound() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GT, 6.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_lt_satisfied() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.LT, 10.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_lt_violated_atBound() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.LT, 6.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 3.0)))).isFalse();
    }

    @Test void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 1.0)
                .isSatisfiedBy(Assignment.of(Map.of(X, 999.0)))).isTrue();
    }

    // --- toString / getRelation ---

    @Test void toString_format() {
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 12.0).toString())
                .isEqualTo("<(x_prod, y_prod), x_prod * y_prod == 12.0>");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var a = ProductConstraint.<Double>builder().variable(X).variable(Y).operator(Operator.GEQ).bound(5.0).build();
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0)).isEqualTo(a);
    }

    // --- propagate: non-propagating operators ---

    @Test void propagate_neq_returnsEmptyMap() {
        var result = ProductConstraint.of(Set.of(X, Y), Operator.NEQ, 6.0).propagate(intervals(1, 5, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_lt_returnsEmptyMap() {
        var result = ProductConstraint.of(Set.of(X, Y), Operator.LT, 6.0).propagate(intervals(1, 5, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_gt_returnsEmptyMap() {
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GT, 6.0).propagate(intervals(1, 5, 1, 5));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: non-positive domain ---

    @Test void propagate_zeroDomainMin_returnsEmptyMap() {
        // X includes 0 — multiplication is non-monotone; skip propagation
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 4.0).propagate(intervals(0, 5, 1, 3));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_negativeDomainMin_returnsEmptyMap() {
        // X has negative min — skip propagation
        var result = ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 4.0).propagate(intervals(-1, 5, 1, 3));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: infeasibility ---

    @Test void propagate_geq_infeasible_boundAboveProductMax() {
        // X∈[1,2], Y∈[1,2], productMax=4, bound=5 > 4 → infeasible
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagate(intervals(1, 2, 1, 2))).isEmpty();
    }

    @Test void propagate_leq_infeasible_boundBelowProductMin() {
        // X∈[2,4], Y∈[2,4], productMin=4, bound=1 < 4 → infeasible
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 1.0).propagate(intervals(2, 4, 2, 4))).isEmpty();
    }

    @Test void propagate_eq_infeasible_boundAboveProductMax() {
        // X∈[1,2], Y∈[1,3], productMax=6, bound=7 > 6 → infeasible
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 7.0).propagate(intervals(1, 2, 1, 3))).isEmpty();
    }

    @Test void propagate_eq_infeasible_boundBelowProductMin() {
        // X∈[2,3], Y∈[2,3], productMin=4, bound=3 < 4 → infeasible
        assertThat(ProductConstraint.of(Set.of(X, Y), Operator.EQ, 3.0).propagate(intervals(2, 3, 2, 3))).isEmpty();
    }

    // --- propagate: GEQ (lower-bound pass only) ---

    @Test void propagate_geq_raisesMinOfOneVariable() {
        // X∈[1,10], Y∈[1,2], bound=8 (GEQ)
        // productMax=20: newMin[X]=8*10/20=4>1→raise; newMin[Y]=8*2/20=0.8<1→no change
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 8.0).propagate(intervals(1, 10, 1, 2)).orElseThrow();
        assertThat(result).containsKey(X);
        assertThat(xDom(result).getMin()).isEqualTo(4.0);
        assertThat(xDom(result).getMax()).isEqualTo(10.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_geq_noChangeWhenAlreadySatisfied() {
        // X∈[4,10], Y∈[2,3], bound=6 (GEQ)
        // productMax=30: newMin[X]=6*10/30=2<4→no change; newMin[Y]=6*3/30=0.6<2→no change
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 6.0).propagate(intervals(4, 10, 2, 3)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: LEQ (upper-bound pass only) ---

    @Test void propagate_leq_lowersMaxOfBothVariables() {
        // X∈[1,10], Y∈[1,10], bound=6 (LEQ)
        // productMin=1: newMax[X]=6*1/1=6<10→clip; newMax[Y]=6*1/1=6<10→clip
        var result = ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 6.0).propagate(intervals(1, 10, 1, 10)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(6.0);
        assertThat(yDom(result).getMax()).isEqualTo(6.0);
    }

    @Test void propagate_leq_noChangeWhenMaxAlreadyTight() {
        // X∈[1,3], Y∈[1,2], bound=12 (LEQ)
        // productMin=1: newMax[X]=12≥3→no change; newMax[Y]=12≥2→no change
        var result = ProductConstraint.of(Set.of(X, Y), Operator.LEQ, 12.0).propagate(intervals(1, 3, 1, 2)).orElseThrow();
        assertThat(result).isEmpty();
    }

    // --- propagate: EQ (both passes) ---

    @Test void propagate_eq_tightensBothBounds() {
        // X∈[1,10], Y∈[1,3], bound=6 (EQ)
        // productMin=1, productMax=30
        // LEQ: newMax[X]=6*1/1=6<10→clip X to [1,6]; newMax[Y]=6*1/1=6≥3→no change
        // GEQ: newMin[X]=6*10/30=2>1→raise (using clipped dom [1,6]); newMin[Y]=0.6<1→no change
        // → X tightened to [2,6]
        var result = ProductConstraint.of(Set.of(X, Y), Operator.EQ, 6.0).propagate(intervals(1, 10, 1, 3)).orElseThrow();
        assertThat(result).containsKey(X);
        assertThat(xDom(result).getMin()).isEqualTo(2.0);
        assertThat(xDom(result).getMax()).isEqualTo(6.0);
        assertThat(result).doesNotContainKey(Y);
    }

    // --- propagate: discrete domain ---

    @Test void propagate_geq_discreteDomain_raisesValues() {
        Variable<Integer> a = F.create("a_pr"), b = F.create("b_pr");
        // a∈[1,5], b∈[1,5], bound=9 (GEQ)
        // productMax=25: newMin[a]=9*5/25=1.8→values<1.8 removed → {2,3,4,5}
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1, 5), b, IntRangeDomain.of(1, 5));
        var result = ProductConstraint.of(Set.of(a, b), Operator.GEQ, 9).propagate(domains).orElseThrow();
        assertThat(result.get(a).contains(1)).isFalse();
        assertThat(result.get(a).contains(2)).isTrue();
        assertThat(result.get(b).contains(1)).isFalse();
        assertThat(result.get(b).contains(2)).isTrue();
    }

    @Test void propagate_eq_discreteDomain_infeasible_noProductEqualsK() {
        // A={2,5}, B={1}, bound=4 (EQ): productMin=2, productMax=5, k=4 in [2,5]
        // LEQ clips A from {2,5} to {2}; GEQ then raises A to need ≥4 → {2}∩[4,5]=∅ → infeasible
        Variable<Integer> a = F.create("a_pr2"), b = F.create("b_pr2");
        var domA = DomainObjectSet.<Integer>builder().value(2).value(5).build();
        var domB = DomainObjectSet.<Integer>builder().value(1).build();
        var domains = Map.<Variable<?>, Domain<?>>of(a, domA, b, domB);
        assertThat(ProductConstraint.of(Set.of(a, b), Operator.EQ, 4).propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 6.0).propagateWithReasons(intervals(4, 10, 2, 3));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test void propagateWithReasons_infeasible_bothSingleton_attributesBoth() {
        // X=[1,1], Y=[2,2], productMax=2, bound=5 (GEQ) > 2 → infeasible; both sides pinned.
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagateWithReasons(intervals(1, 1, 2, 2));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(X, 1.0), Map.entry(Y, 2.0));
    }

    @Test void propagateWithReasons_infeasible_notAllSingleton_returnsEmptyReason() {
        // X∈[1,2], Y∈[1,2]: infeasible (matches propagate_geq_infeasible_boundAboveProductMax()),
        // but neither side is pinned to a single value, so no variable-value pair can be blamed.
        var result = ProductConstraint.of(Set.of(X, Y), Operator.GEQ, 5.0).propagateWithReasons(intervals(1, 2, 1, 2));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    // --- solver integration ---

    @Test void solver_factorPairsOf12() {
        // Find all integer pairs (x,y) where x*y=12, x∈[1,12], y∈[1,12]
        Variable<Integer> x = F.create("px"), y = F.create("py");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 12))
                .variableDomain(y, IntRangeDomain.of(1, 12))
                .productConstraint(Set.of(x, y), Operator.EQ, 12)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        // (1,12),(2,6),(3,4),(4,3),(6,2),(12,1)
        assertThat(solutions).hasSize(6);
        assertThat(solutions).allMatch(a -> a.getValue(x).orElseThrow() * a.getValue(y).orElseThrow() == 12);
    }
}
