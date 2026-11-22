package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PowerVariableConstraintTest {
    static final Variable.Factory F = new Variable.Factory();
    static final Variable<Integer> X = F.create("x_pow");
    static final Variable<Integer> T = F.create("t_pow");

    @SuppressWarnings("unchecked")
    private static List<Integer> values(Map<Variable<?>, Domain<?>> result, Variable<Integer> variable) {
        return ((DiscreteDomain<Integer>) result.get(variable)).toList();
    }

    private static Map<Variable<?>, Domain<?>> propagate(int exponent, Operator operator, Domain<Integer> x, Domain<Integer> t) {
        return PowerVariableConstraint.of(X, exponent, operator, T).propagate(Map.of(X, x, T, t)).orElseThrow();
    }

    // --- construction, isSatisfiedBy, relation ---

    @Test void of_exponentBelowTwo_failsItsPrecondition() {
        assertThatThrownBy(() -> PowerVariableConstraint.of(X, 1, Operator.EQ, T)).isInstanceOf(AssertionError.class);
    }

    @Test void of_equalsTheBuiltInstance() {
        var built = PowerVariableConstraint.<Integer>builder().left(X).right(T).exponent(3).operator(Operator.EQ).build();
        assertThat(PowerVariableConstraint.of(X, 3, Operator.EQ, T)).isEqualTo(built);
        assertThat(PowerVariableConstraint.of(X, 3, Operator.EQ, T)).isNotEqualTo(PowerVariableConstraint.of(X, 2, Operator.EQ, T));
        assertThat(built.getExponent()).isEqualTo(3);
    }

    @Test void isSatisfiedBy_comparesThePower() {
        assertThat(PowerVariableConstraint.of(X, 3, Operator.EQ, T).isSatisfiedBy(-2, -8)).isTrue();
        assertThat(PowerVariableConstraint.of(X, 3, Operator.EQ, T).isSatisfiedBy(-2, 8)).isFalse();
        assertThat(PowerVariableConstraint.of(X, 4, Operator.LEQ, T).isSatisfiedBy(2, 16)).isTrue();
        assertThat(PowerVariableConstraint.of(X, 4, Operator.GEQ, T).isSatisfiedBy(2, 17)).isFalse();
    }

    @Test void isSatisfiedBy_negativeZeroToAnOddPower_equalsZero() {
        Variable<Double> x = F.create("dx_pow0");
        Variable<Double> t = F.create("dt_pow0");
        assertThat(PowerVariableConstraint.of(x, 3, Operator.EQ, t).isSatisfiedBy(-0.0, 0.0)).isTrue();
    }

    @Test void getRelation_showsTheExponent() {
        assertThat(PowerVariableConstraint.of(X, 3, Operator.LEQ, T).getRelation()).contains("^3").contains("<=");
    }

    @Test void propagate_neq_doesNotPropagate() {
        assertThat(propagate(3, Operator.NEQ, IntRangeDomain.of(-5, 5), IntRangeDomain.of(0, 1))).isEmpty();
    }

    // --- odd exponent: monotone, so both directions clip the operand ---

    @Test void propagate_odd_eq_clipsTheOperandByBothSignedRoots() {
        // x^3 in [-8, 64] -> x in [-2, 4]; Math.pow(64, 1/3.0) is 3.9999999999999996, so without
        // the slack 4 would be lost.
        var result = propagate(3, Operator.EQ, IntRangeDomain.of(-5, 5), IntRangeDomain.of(-8, 64));
        assertThat(values(result, X)).containsExactly(-2, -1, 0, 1, 2, 3, 4);
        assertThat(result).doesNotContainKey(T);
    }

    @Test void propagate_odd_leq_clipsTheOperandMaxAndRaisesTheTargetMin() {
        // x^3 <= t <= 8 -> x <= 2; t >= (-5)^3 = -125, but t's max stays (x=-5, t=8)
        var result = propagate(3, Operator.LEQ, IntRangeDomain.of(-5, 5), IntRangeDomain.of(-200, 8));
        assertThat(values(result, X)).containsExactly(-5, -4, -3, -2, -1, 0, 1, 2);
        assertThat(values(result, T)).startsWith(-125).endsWith(8);
    }

    @Test void propagate_odd_geq_clipsTheOperandMinAndLowersTheTargetMax() {
        // x^3 >= t >= -8 -> x >= -2; t <= 5^3 = 125, but t's min stays (x=5, t=-8)
        var result = propagate(3, Operator.GEQ, IntRangeDomain.of(-5, 5), IntRangeDomain.of(-8, 200));
        assertThat(values(result, X)).containsExactly(-2, -1, 0, 1, 2, 3, 4, 5);
        assertThat(values(result, T)).startsWith(-8).endsWith(125);
    }

    // --- even exponent: symmetric, so only <= clips the operand ---

    @Test void propagate_even_eq_clipsTheOperandSymmetrically() {
        var result = propagate(4, Operator.EQ, IntRangeDomain.of(-3, 3), IntRangeDomain.of(0, 16));
        assertThat(values(result, X)).containsExactly(-2, -1, 0, 1, 2);
        assertThat(result).doesNotContainKey(T);
    }

    @Test void propagate_even_leq_entirelyNegativeOperand_raisesTheTargetMinToTheSmallerMagnitude() {
        // x in [-3,-1]: x^2 is at least 1, so x^2 <= t needs t >= 1; root(100) = 10 leaves x alone
        var result = propagate(2, Operator.LEQ, IntRangeDomain.of(-3, -1), IntRangeDomain.of(0, 100));
        assertThat(values(result, T)).startsWith(1).endsWith(100);
        assertThat(result).doesNotContainKey(X);
    }

    @Test void propagate_even_geq_positiveOperand_lowersTheTargetMaxOnly() {
        var result = propagate(2, Operator.GEQ, IntRangeDomain.of(1, 3), IntRangeDomain.of(0, 100));
        assertThat(values(result, T)).startsWith(0).endsWith(9);
        assertThat(result).doesNotContainKey(X);
    }

    // --- infeasibility ---

    @Test void propagate_leq_smallestPowerAboveTheTarget_isInfeasible() {
        assertThat(PowerVariableConstraint.of(X, 3, Operator.LEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(2, 3), T, IntRangeDomain.of(0, 5)))).isEmpty();
    }

    @Test void propagate_geq_largestPowerBelowTheTarget_isInfeasible() {
        assertThat(PowerVariableConstraint.of(X, 3, Operator.GEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(0, 2), T, IntRangeDomain.of(10, 20)))).isEmpty();
    }

    @Test void propagate_eq_targetNarrowingEmptiesAGappedTarget_isInfeasible() {
        // x^3 reaches [-8, 8], which holds neither -100 nor 100
        assertThat(PowerVariableConstraint.of(X, 3, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-2, 2), T, NumericDiscreteDomain.of(-100, 100)))).isEmpty();
    }

    @Test void propagate_leq_operandNarrowingEmptiesAGappedOperand_isInfeasible() {
        // x^2 <= 9 -> |x| <= 3, which holds neither -10 nor 10
        assertThat(PowerVariableConstraint.of(X, 2, Operator.LEQ, T)
                .propagate(Map.of(X, NumericDiscreteDomain.of(-10, 10), T, IntRangeDomain.of(0, 9)))).isEmpty();
    }

    // --- continuous domains ---

    @Test void propagate_bounded_odd_leq_clipsTheOperandToTheRoot() {
        Variable<Double> x = F.create("dx_pow");
        Variable<Double> t = F.create("dt_pow");
        var result = PowerVariableConstraint.of(x, 3, Operator.LEQ, t)
                .propagate(Map.of(x, IntervalDomain.of(-5.0, 5.0), t, IntervalDomain.of(-200.0, 8.0))).orElseThrow();
        assertThat(((IntervalDomain) result.get(x)).getMax()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_returnsAReason() {
        var gappedT = IntRangeDomain.of(10, 20).toBuilder().delete(15).build();
        var result = PowerVariableConstraint.of(X, 3, Operator.EQ, T)
                .propagateWithReasons(Map.of(X, IntRangeDomain.of(-2, 2), T, gappedT));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
    }
}
