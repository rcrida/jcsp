package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SquareConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x_sq");

    static Assignment a(int x) {
        return Assignment.of(Map.of(X, x));
    }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.EQ, 9).isSatisfiedBy(a(3))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(SquareConstraint.of(X, Operator.EQ, 9).isSatisfiedBy(a(4))).isFalse();
    }

    @Test void isSatisfiedBy_eq_negativeOperandSquaresPositive() {
        assertThat(SquareConstraint.of(X, Operator.EQ, 9).isSatisfiedBy(a(-3))).isTrue();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.LEQ, 10).isSatisfiedBy(a(3))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(SquareConstraint.of(X, Operator.LEQ, 10).isSatisfiedBy(a(4))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.GEQ, 10).isSatisfiedBy(a(4))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(SquareConstraint.of(X, Operator.GEQ, 10).isSatisfiedBy(a(2))).isFalse();
    }

    @Test void isSatisfiedBy_neq_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.NEQ, 9).isSatisfiedBy(a(4))).isTrue();
    }

    @Test void isSatisfiedBy_neq_violated() {
        assertThat(SquareConstraint.of(X, Operator.NEQ, 9).isSatisfiedBy(a(3))).isFalse();
    }

    @Test void isSatisfiedBy_lt_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.LT, 10).isSatisfiedBy(a(3))).isTrue();
    }

    @Test void isSatisfiedBy_lt_violated() {
        assertThat(SquareConstraint.of(X, Operator.LT, 9).isSatisfiedBy(a(3))).isFalse();
    }

    @Test void isSatisfiedBy_gt_satisfied() {
        assertThat(SquareConstraint.of(X, Operator.GT, 8).isSatisfiedBy(a(3))).isTrue();
    }

    @Test void isSatisfiedBy_gt_violated() {
        assertThat(SquareConstraint.of(X, Operator.GT, 9).isSatisfiedBy(a(3))).isFalse();
    }

    // --- getRelation / toString ---

    @Test void getRelation_includesSquareNotationAndOperatorSymbol() {
        assertThat(SquareConstraint.of(X, Operator.LEQ, 9).getRelation()).contains("^2").contains("<=").contains("9");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var built = SquareConstraint.<Integer>builder().variable(X).operator(Operator.LEQ).bound(9).build();
        assertThat(SquareConstraint.of(X, Operator.LEQ, 9)).isEqualTo(built);
    }

    // --- propagate: NEQ skipped ---

    @Test void propagate_neq_noChange() {
        var result = SquareConstraint.of(X, Operator.NEQ, 9).propagate(Map.of(X, IntRangeDomain.of(-5, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: LEQ/LT ---

    @Test void propagate_leq_clipsSymmetricRange() {
        // domain=[-5,5], x^2<=9 -> [-3,3]
        var result = SquareConstraint.of(X, Operator.LEQ, 9).propagate(Map.of(X, IntRangeDomain.of(-5, 5))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(-3, -2, -1, 0, 1, 2, 3);
    }

    @Test void propagate_leq_negativeBound_infeasible() {
        assertThat(SquareConstraint.of(X, Operator.LEQ, -1).propagate(Map.of(X, IntRangeDomain.of(-5, 5)))).isEmpty();
    }

    @Test void propagate_leq_alreadyTight_noChange() {
        var result = SquareConstraint.of(X, Operator.LEQ, 100).propagate(Map.of(X, IntRangeDomain.of(-5, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_leq_narrowingEmptiesGappedDomain_infeasible() {
        // domain={-5,5} (gap), x^2<=9 -> narrows to [-3,3], neither -5 nor 5 lies in it
        var domain = IntRangeDomain.of(-5, 5).toBuilder().delete(-4).delete(-3).delete(-2).delete(-1).delete(0)
                .delete(1).delete(2).delete(3).delete(4).build();
        var result = SquareConstraint.of(X, Operator.LEQ, 9).propagate(Map.of(X, domain));
        assertThat(result).isEmpty();
    }

    @Test void propagate_lt_sameAsLeq() {
        var result = SquareConstraint.of(X, Operator.LT, 9).propagate(Map.of(X, IntRangeDomain.of(-5, 5))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(-3, -2, -1, 0, 1, 2, 3);
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_narrowsToRootsRange() {
        var result = SquareConstraint.of(X, Operator.EQ, 9).propagate(Map.of(X, IntRangeDomain.of(-5, 5))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(-3, -2, -1, 0, 1, 2, 3);
    }

    @Test void propagate_eq_unreachableBound_infeasible() {
        // domain=[2,5]: sqLo=4, sqHi=25; bound=1 is below sqLo -> unreachable
        assertThat(SquareConstraint.of(X, Operator.EQ, 1).propagate(Map.of(X, IntRangeDomain.of(2, 5)))).isEmpty();
    }

    // --- propagate: GEQ/GT ---

    @Test void propagate_geq_feasible_noNarrowing() {
        var result = SquareConstraint.of(X, Operator.GEQ, 4).propagate(Map.of(X, IntRangeDomain.of(-5, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_geq_infeasible() {
        // domain=[-2,2]: sqHi=4 < bound=10 -> infeasible
        assertThat(SquareConstraint.of(X, Operator.GEQ, 10).propagate(Map.of(X, IntRangeDomain.of(-2, 2)))).isEmpty();
    }

    @Test void propagate_gt_feasible() {
        var result = SquareConstraint.of(X, Operator.GT, 3).propagate(Map.of(X, IntRangeDomain.of(-5, 5)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test void propagate_gt_infeasible() {
        assertThat(SquareConstraint.of(X, Operator.GT, 10).propagate(Map.of(X, IntRangeDomain.of(-2, 2)))).isEmpty();
    }

    // --- propagate: BoundedDomain (IntervalDomain) ---

    static final Variable<Double> DX = F.create("dx_sq");

    @Test void propagate_bounded_leq_clipsToSymmetricInterval() {
        var result = SquareConstraint.of(DX, Operator.LEQ, 9.0).propagate(Map.of(DX, IntervalDomain.of(-5.0, 5.0))).orElseThrow();
        var narrowed = (IntervalDomain) result.get(DX);
        assertThat(narrowed.getMin()).isEqualTo(-3.0);
        assertThat(narrowed.getMax()).isEqualTo(3.0);
    }

    @Test void propagate_bounded_domainNotStraddlingZero_sqLoIsSmallerEndpoint() {
        // domain=[2,5] (all positive): sqLo=min(4,25)=4, sqHi=25; bound=1 (< sqLo) -> unreachable for EQ
        assertThat(SquareConstraint.of(DX, Operator.EQ, 1.0).propagate(Map.of(DX, IntervalDomain.of(2.0, 5.0)))).isEmpty();
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_singleton_citesCurrentBoundsAsRange() {
        // RangeNogoodConstraint#fromCurrentBounds is tried first and succeeds whenever every cited
        // variable's current bounds are safely citable (a singleton domain always qualifies), so a
        // singleton infeasibility is cited as a range, not falling to the ValueSetNogoodConstraint
        // fallback -- matching every other propagator's explainInfeasible in this codebase.
        var result = SquareConstraint.of(X, Operator.LEQ, -1).propagateWithReasons(Map.of(X, IntRangeDomain.of(5, 5)));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
        assertThat(result.reason().isSatisfiedBy(a(5))).isFalse();
        assertThat(result.reason().isSatisfiedBy(a(0))).isTrue();
    }

    @Test void explainInfeasible_nonSingleton_citesValueSet() {
        var domain = IntRangeDomain.of(-5, 5);
        var result = SquareConstraint.of(X, Operator.LEQ, -1).propagateWithReasons(Map.of(X, domain));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
    }
}
