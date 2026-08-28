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

    @Test void explainInfeasible_boundedDomain_citesCurrentBounds() {
        // RangeNogoodConstraint#fromCurrentBounds works for any NumericDomain, IntervalDomain
        // (BoundedDomain) included, not just discrete ones.
        var result = UnaryComparatorConstraint.of(DX, Operator.GEQ, 20.0).explainInfeasible(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint.of(
                Map.of(DX, IntervalDomain.of(0.0, 10.0))));
    }

    @Test void explainInfeasible_discreteDomain_citesCurrentBounds() {
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 10)
                .explainInfeasible(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint.of(
                Map.of(X, IntervalDomain.of(1, 5))));
    }

    @Test void explainInfeasible_gappedDiscreteDomain_citesExactValueSet() {
        // {1,5} has a gap at 2,3,4 -- RangeNogoodConstraint's own gaplessness gate declines, falling
        // through to ValueSetNogoodConstraint's exact citation instead.
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 10)
                .explainInfeasible(Map.of(X, io.github.rcrida.jcsp.domains.DiscreteDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint.of(
                Map.of(X, java.util.Set.of(1, 5))));
    }

    @Test void propagate_noChange_returnsEmptyMap() {
        var result = UnaryComparatorConstraint.of(DX, Operator.GEQ, 0.0).propagate(domains(0.0, 10.0));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate() tests for discrete domains ---

    @Test void propagate_discreteDomain_geq_deletesBelowValue() {
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(((io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(X)).toList())
                .containsExactly(3, 4, 5);
    }

    @Test void propagate_discreteDomain_leq_deletesAboveValue() {
        var result = UnaryComparatorConstraint.of(X, Operator.LEQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(((io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(X)).toList())
                .containsExactly(1, 2, 3);
    }

    @Test void propagate_discreteDomain_eq_narrowsToSingleton() {
        var result = UnaryComparatorConstraint.of(X, Operator.EQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(((io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(X)).toList())
                .containsExactly(3);
    }

    @Test void propagate_discreteDomain_eq_valueOutsideDomain_infeasible() {
        var result = UnaryComparatorConstraint.of(X, Operator.EQ, 9)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_discreteDomain_eq_valueInGap_infeasible() {
        // {1,5} has a gap at 2,3,4 -- value=3 sits numerically inside [1,5] (so newMin<=newMax holds,
        // unlike the out-of-domain-entirely case above, which returns earlier via the newMin>newMax
        // check) but isn't actually present, so narrowing to [3,3] empties the domain via
        // NumericBounds#narrow itself -- exercises pruned.get().isEmpty() specifically.
        var result = UnaryComparatorConstraint.of(X, Operator.EQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.DiscreteDomain.of(1, 5)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_discreteDomain_geq_infeasible() {
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 10)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isEmpty();
    }

    @Test void propagate_discreteDomain_noChange_returnsEmptyMap() {
        var result = UnaryComparatorConstraint.of(X, Operator.GEQ, 0)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_discreteDomain_neq_deletesValue() {
        var result = UnaryComparatorConstraint.of(X, Operator.NEQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(((io.github.rcrida.jcsp.domains.DiscreteDomain<Integer>) result.get().get(X)).toList())
                .containsExactly(1, 2, 4, 5);
    }

    @Test void propagate_discreteDomain_neq_valueNotPresent_noChange() {
        var result = UnaryComparatorConstraint.of(X, Operator.NEQ, 9)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(1, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_discreteDomain_neq_singletonEqualToValue_infeasible() {
        var result = UnaryComparatorConstraint.of(X, Operator.NEQ, 3)
                .propagate(Map.of(X, io.github.rcrida.jcsp.domains.IntRangeDomain.of(3, 3)));
        assertThat(result).isEmpty();
    }

}
