package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SquareVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("x_sqv");
    static final Variable<Integer> T = F.create("t_sqv");

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T).isSatisfiedBy(3, 9)).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T).isSatisfiedBy(3, 8)).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(SquareVariableConstraint.of(X, Operator.LEQ, T).isSatisfiedBy(3, 10)).isTrue();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(SquareVariableConstraint.of(X, Operator.GEQ, T).isSatisfiedBy(4, 10)).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(SquareVariableConstraint.of(X, Operator.GEQ, T).isSatisfiedBy(2, 10)).isFalse();
    }

    // --- getRelation ---

    @Test void getRelation_includesSquareNotationAndOperatorSymbol() {
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T).getRelation()).contains("^2").contains("=");
    }

    // --- of() factory ---

    @Test void of_createsEquivalentConstraint() {
        var built = SquareVariableConstraint.<Integer>builder().left(X).right(T).operator(Operator.EQ).build();
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T)).isEqualTo(built);
    }

    // --- propagate: NEQ skipped ---

    @Test void propagate_neq_noChange() {
        var result = SquareVariableConstraint.of(X, Operator.NEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 25)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: EQ ---

    @Test void propagate_eq_narrowsTargetAndOperand() {
        // x=[-5,5] (straddles zero): sqLo=0, sqHi=25; t=[0,100] -> bounds-consistency narrows t's
        // *range* to [0,25] (not full GAC -- non-perfect-square values in between stay, matching
        // MaxVariableConstraint/MinVariableConstraint's own bounds-only tradeoff for non-EQ-coverage
        // cases); x stays [-5,5] since sqrt(25)=5 is already its current max.
        var result = SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 100))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(T)).toList())
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25);
        assertThat(result.containsKey(X)).isFalse();
    }

    @Test void propagate_eq_narrowsOperandFromTightTarget() {
        // x=[-5,5], t=[0,9] -> newTargetHi=9 (already tight, no change to t needed if 9 already max)
        // but operand narrows to [-3,3] via sqrt(9)=3
        var result = SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 9))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(-3, -2, -1, 0, 1, 2, 3);
    }

    @Test void propagate_eq_infeasible_targetBelowAchievableMin() {
        // x=[2,5] (all positive): sqLo=4, sqHi=25; t=[0,3] -> newTargetLo=max(0,4)=4 > targetHi=3 -> infeasible
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(2, 5), T, IntRangeDomain.of(0, 3)))).isEmpty();
    }

    @Test void propagate_eq_infeasible_targetAboveAchievableMax() {
        // x=[-2,2]: sqHi=4; t=[10,20] -> newTargetHi=min(20,4)=4 < targetLo=10 -> infeasible
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-2, 2), T, IntRangeDomain.of(10, 20)))).isEmpty();
    }

    @Test void propagate_domainEntirelyNegative_sqLoIsSmallerMagnitudeEndpoint() {
        // x=[-5,-2] (entirely negative, doesn't straddle zero): sqLo=min(25,4)=4, distinct from the
        // straddles-zero case (sqLo=0) and the entirely-positive case (both exercised elsewhere in
        // this class already, e.g. x=[2,5] above). t=[0,20] is wide enough that narrowing its min up
        // to sqLo=4 stays feasible (unlike a narrower target range, which would invert lo>hi and
        // report infeasible instead -- not what this test is checking).
        var result = SquareVariableConstraint.of(X, Operator.GEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, -2), T, IntRangeDomain.of(0, 20))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(T)).toList()).doesNotContain(0, 1, 2, 3);
    }

    @Test void propagate_eq_targetNarrowingEmptiesGappedDomain_infeasible() {
        // t's live values are {-10,40} (a gap domain spanning far past the narrowed range on both
        // sides), x=[-5,5]: sqLo=0, sqHi=25. Feasibility checks pass (0<=40, 25>=-10), so the code
        // proceeds to narrow t to [0,25] -- but neither -10 nor 40 lies in that range, so narrowing
        // empties t's value set even though [0,25] itself is a non-empty numeric range: exercises
        // prunedTarget.get().isEmpty() specifically, distinct from propagate_eq_infeasible_* above
        // (which are caught by the earlier bounds-only feasibility check, never reaching narrow()).
        var gappedTarget = NumericDiscreteDomain.of(-10, 40);
        assertThat(SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, gappedTarget))).isEmpty();
    }

    @Test void propagate_leq_operandNarrowingEmptiesGappedDomain_infeasible() {
        // x's live values are {-10,10} (a gap domain), t=[0,9]: sqLo=0 (straddles zero), so the
        // target-side feasibility/narrowing passes through unchanged (t is already tight). left then
        // narrows via sqrt(9)=3 to [-3,3] -- but neither -10 nor 10 lies in that range, emptying x's
        // value set: exercises prunedLeft.get().isEmpty() specifically.
        var gappedOperand = NumericDiscreteDomain.of(-10, 10);
        assertThat(SquareVariableConstraint.of(X, Operator.LEQ, T)
                .propagate(Map.of(X, gappedOperand, T, IntRangeDomain.of(0, 9)))).isEmpty();
    }

    // --- propagate: LEQ ---

    @Test void propagate_leq_narrowsTargetMax() {
        // x=[-5,5]: sqHi=25; t=[0,100] -> t narrows to [0,25]; x unaffected since sqrt(25)=5=xMax already
        var result = SquareVariableConstraint.of(X, Operator.LEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 100))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(T)).toList()).contains(25).doesNotContain(26);
        assertThat(result.containsKey(X)).isFalse();
    }

    @Test void propagate_leq_narrowsOperandFromTargetMax() {
        // x=[-5,5], t=[0,9]: x^2<=t.max=9 -> x narrows to [-3,3]
        var result = SquareVariableConstraint.of(X, Operator.LEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 9))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(X)).toList()).containsExactly(-3, -2, -1, 0, 1, 2, 3);
    }

    @Test void propagate_leq_noChange() {
        var result = SquareVariableConstraint.of(X, Operator.LEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-3, 3), T, IntRangeDomain.of(0, 9)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: GEQ (no operand narrowing) ---

    @Test void propagate_geq_narrowsTargetMinOnly_leavesOperandUntouched() {
        // x=[-5,5]: sqLo=0 (straddles zero); t=[10,100] -> newTargetLo=max(10,0)=10 unchanged
        // Use a domain not straddling zero to get a nonzero sqLo: x=[2,5]: sqLo=4
        var result = SquareVariableConstraint.of(X, Operator.GEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(2, 5), T, IntRangeDomain.of(0, 100))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(T)).toList()).doesNotContain(0, 1, 2, 3);
        assertThat(result.containsKey(X)).isFalse();
    }

    @Test void propagate_geq_infeasible() {
        // x=[-2,2]: sqHi=4; t=[10,20] -> sqHi < targetLo -> infeasible
        assertThat(SquareVariableConstraint.of(X, Operator.GEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-2, 2), T, IntRangeDomain.of(10, 20)))).isEmpty();
    }

    @Test void propagate_geq_feasible_noNarrowing() {
        var result = SquareVariableConstraint.of(X, Operator.GEQ, T)
                .propagate(Map.of(X, IntRangeDomain.of(-5, 5), T, IntRangeDomain.of(0, 10)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate: BoundedDomain (IntervalDomain) ---

    static final Variable<Double> DX = F.create("dx_sqv");
    static final Variable<Double> DT = F.create("dt_sqv");

    @Test void propagate_bounded_leq_narrowsBoth() {
        var result = SquareVariableConstraint.of(DX, Operator.LEQ, DT)
                .propagate(Map.of(DX, IntervalDomain.of(-5.0, 5.0), DT, IntervalDomain.of(0.0, 9.0))).orElseThrow();
        var narrowedX = (IntervalDomain) result.get(DX);
        assertThat(narrowedX.getMin()).isEqualTo(-3.0);
        assertThat(narrowedX.getMax()).isEqualTo(3.0);
    }

    // --- explainInfeasible ---

    @Test void explainInfeasible_returnsNonNullReason() {
        // T's domain is gapped (missing 15), so it isn't "safe to cite as range"
        // (RangeNogoodConstraint's own gate requires size == max-min+1) -- fromCurrentBounds
        // declines the whole citation, exercising the ValueSetNogoodConstraint fallback instead.
        var gappedT = IntRangeDomain.of(10, 20).toBuilder().delete(15).build();
        var result = SquareVariableConstraint.of(X, Operator.EQ, T)
                .propagateWithReasons(Map.of(X, IntRangeDomain.of(-2, 2), T, gappedT));
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotNull();
    }

}
