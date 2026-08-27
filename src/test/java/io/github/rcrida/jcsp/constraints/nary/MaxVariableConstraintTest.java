package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MaxVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_mv");
    static final Variable<Double> Y = F.create("y_mv");
    static final Variable<Double> T = F.create("t_mv");

    static MaxVariableConstraint<Double> of(Operator operator) {
        return MaxVariableConstraint.of(Set.of(X, Y), operator, T);
    }

    static Map<Variable<?>, Domain<?>> domains(double xLo, double xHi, double yLo, double yHi, double tLo, double tHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi), T, IntervalDomain.of(tLo, tHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }
    static IntervalDomain tDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(T); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(X, 7.0, Y, 3.0, T, 7.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 6.0, T, 7.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 7.0, T, 7.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 8.0, Y, 3.0, T, 7.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 5.0, T, 5.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 4.0, T, 5.0)))).isFalse();
    }

    @Test void isSatisfiedBy_targetUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 10.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_maxedVariableUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 10.0, T, 3.0)))).isTrue();
    }

    // --- toString / of() ---

    @Test void testToString() {
        assertThat(of(Operator.LEQ).toString()).isEqualTo("<(t_mv, x_mv, y_mv), max(x_mv, y_mv) <= t_mv>");
    }

    @Test void of_createsEquivalentConstraint() {
        assertThat(MaxVariableConstraint.of(Set.of(X, Y), Operator.LEQ, T)).isEqualTo(of(Operator.LEQ));
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

    // --- propagate: LEQ (max(vars) <= target) ---

    @Test void propagate_leq_wideDomains_noChange() {
        var result = of(Operator.LEQ).propagate(domains(0, 5, 0, 5, 0, 20)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_leq_raisesTargetLowerBoundFromVars_upperBoundUntouched() {
        // X,Y in [0,10] -> mLo=0, mHi=10; T in [0,20]: T's lower bound stays 0 (mLo=0 doesn't raise
        // it), and LEQ never narrows T's upper bound from vars (that's GEQ's job) -- unchanged.
        var result = of(Operator.LEQ).propagate(domains(3, 10, 4, 8, 0, 20)).orElseThrow();
        // mLo = max(3,4) = 4 -> T's lower bound raised to 4; T's upper bound (20) untouched.
        assertThat(tDom(result).getMin()).isEqualTo(4.0);
        assertThat(tDom(result).getMax()).isEqualTo(20.0);
    }

    @Test void propagate_leq_clipsVarsUpperBoundFromTarget() {
        // X,Y in [0,10]; T in [0,5]: both vars' max clip to 5 (T's current max), T unaffected
        // upward since mLo=0 doesn't raise T's lower bound past its own 0.
        var result = of(Operator.LEQ).propagate(domains(0, 10, 0, 10, 0, 5)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(yDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(T);
    }

    @Test void propagate_leq_infeasible_mLoExceedsTargetMax() {
        // X in [6,10], Y in [7,9] -> mLo=7; T in [0,5] -> tHi=5 < 7 -> infeasible
        assertThat(of(Operator.LEQ).propagate(domains(6, 10, 7, 9, 0, 5))).isEmpty();
    }

    // --- propagate: GEQ (max(vars) >= target) ---

    @Test void propagate_geq_lowersTargetUpperBoundFromVars_lowerBoundUntouched() {
        // X,Y in [0,3]/[0,4] -> mHi=4; T in [0,20]: T's upper bound lowered to 4, T's lower bound
        // (0) untouched -- that's LEQ's job.
        var result = of(Operator.GEQ).propagate(domains(0, 3, 0, 4, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMax()).isEqualTo(4.0);
        assertThat(tDom(result).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_geq_forcesMinUpWhenOnlyOneReaches() {
        // X in [0,10], Y in [0,3] -> only X can reach T's min (5); X's min raised to 5
        var result = of(Operator.GEQ).propagate(domains(0, 10, 0, 3, 5, 20)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_geq_noForcingWhenMinAlreadyAtBound() {
        // X in [5,10], Y in [0,3]; T's lower bound 5: only X reaches, but X.min=5 already >= 5 ->
        // no update needed.
        var result = of(Operator.GEQ).propagate(domains(5, 10, 0, 3, 5, 20)).orElseThrow();
        assertThat(result).doesNotContainKey(X);
    }

    @Test void propagate_geq_noForcingWhenMultipleCanReach() {
        var result = of(Operator.GEQ).propagate(domains(0, 10, 0, 10, 5, 5)).orElseThrow();
        assertThat(result).doesNotContainKey(X);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_geq_infeasible_mHiBelowTargetMin() {
        // X in [0,3], Y in [0,4] -> mHi=4; T in [5,10] -> tLo=5 > 4 -> infeasible
        assertThat(of(Operator.GEQ).propagate(domains(0, 3, 0, 4, 5, 10))).isEmpty();
    }

    // --- propagate: EQ (both directions) ---

    @Test void propagate_eq_narrowsTargetBothDirections() {
        // X in [3,10], Y in [4,8] -> mLo=4, mHi=10; T in [0,20] narrows to [4,10]
        var result = of(Operator.EQ).propagate(domains(3, 10, 4, 8, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(4.0);
        assertThat(tDom(result).getMax()).isEqualTo(10.0);
    }

    @Test void propagate_eq_clipsVarsAndForcesSingleReacher() {
        // X in [0,10], Y in [0,3]; T=[5,5] (already exactly converged: mLo=0 doesn't raise T's
        // lower bound past 0's own irrelevance here since newTLo=max(5,0)=5=tLo, and mHi=10 doesn't
        // lower T's upper bound below its own 5 -- so T itself is untouched by this call).
        // Upper-clip: X's max (10) > tHi (5) -> clips to [0,5]; Y's max (3) <= 5 -> unchanged.
        // Lower-force: only X's (clipped) max (5) reaches T's min (5) -- Y's max (3) doesn't -- so
        // X alone is forced up to exactly 5.
        var result = of(Operator.EQ).propagate(domains(0, 10, 0, 3, 5, 5)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(xDom(result).getMin()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
        assertThat(result).doesNotContainKey(T);
    }

    @Test void propagate_eq_infeasible_mLoExceedsTargetMax() {
        assertThat(of(Operator.EQ).propagate(domains(6, 10, 7, 9, 0, 5))).isEmpty();
    }

    @Test void propagate_eq_infeasible_mHiBelowTargetMin() {
        assertThat(of(Operator.EQ).propagate(domains(0, 3, 0, 4, 5, 10))).isEmpty();
    }

    // --- propagate: discrete domain gap forces infeasibility in the lower-force pass ---

    @Test void propagate_eq_discreteDomain_infeasible_noValueEqualsTarget() {
        // a: {0,1,2,4} (gap at 3), b: [0,2], target: [3,3] (singleton). GEQ alone could never hit
        // this: the lower-force pass's forced upper bound is always the reacher's own current max,
        // which for a real materialized discrete domain is always a present value -- never empty.
        // Only EQ can, because its upper-clip pass runs first and *mutates* the tracked max to
        // target's bound (tHi=3) before the lower-force pass ever reads it:
        //   upper-clip: a's max (4) > tHi (3) -> a narrows to [0,3] -> {0,1,2} (4 removed); tracked
        //     max for a becomes 3 (not a's own real max any more). b's max (2) <= 3 -> unchanged.
        //   lower-force: only a's tracked max (3) >= tLo (3) -- b's max (2) doesn't -- so a is the
        //     sole reacher; a's min (0) < 3 -> force narrow(a's now-clipped {0,1,2}, 3, 3) -> empty
        //     (no 3 in {0,1,2}) -> infeasible.
        Variable<Integer> a = F.create("a_mv_gap"), b = F.create("b_mv_gap"), t = F.create("t_mv_gap");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(0, 1, 2, 4), b, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(3, 3));
        assertThat(MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    // --- propagate: EQ full GAC (coverage pruning beyond bounds consistency) ---

    @Test void propagate_eq_discreteDomain_coverageFiltersUnsupportedTargetValue() {
        // a,b both even-valued {2,4,6}; target {3,4,5}: 3 and 5 are within [mLo,mHi]=[2,6] so plain
        // bounds consistency would keep them, but neither is literally achievable by any variable
        // (both only ever produce even maxima) -- only 4 survives coverage filtering. a/b's shared
        // value 6 also drops out (target's own domain never reached that high to begin with, 5
        // being its max) -- the same clip plain bounds consistency's upper pass would apply too,
        // just via target's true achievable-coverage max (4) rather than its raw domain max (5).
        Variable<Integer> a = F.create("a_mv_cov1"), b = F.create("b_mv_cov1"), t = F.create("t_mv_cov1");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 4, 6), b, DiscreteDomain.of(2, 4, 6), t, DiscreteDomain.of(3, 4, 5));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains).orElseThrow();
        assertThat(result.get(t)).isEqualTo(DiscreteDomain.of(4));
        assertThat(result.get(a)).isEqualTo(DiscreteDomain.of(2, 4));
        assertThat(result.get(b)).isEqualTo(DiscreteDomain.of(2, 4));
    }

    @Test void propagate_eq_discreteDomain_coverageFiltersUnsupportedVariableValue() {
        // a={2,5,9}, b={1,2} (b can never exceed 2); t={2,9}. a=5 would force max(a,b)=5 (b<=2<5),
        // but 5 is not in t's domain -- unsupported, even though 5 <= mHi=9 so plain bounds
        // consistency (which only clips a's max down to t's own max, 9) would never remove it.
        Variable<Integer> a = F.create("a_mv_cov2"), b = F.create("b_mv_cov2"), t = F.create("t_mv_cov2");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 5, 9), b, DiscreteDomain.of(1, 2), t, DiscreteDomain.of(2, 9));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains).orElseThrow();
        assertThat(result.get(a)).isEqualTo(DiscreteDomain.of(2, 9));
        assertThat(result).doesNotContainKey(b);
        assertThat(result).doesNotContainKey(t);
    }

    @Test void propagate_eq_discreteDomain_coverage_multipleCoverersNoForcing() {
        // Both a and b contain the shared top value 9 -- neither is the "sole" coverer, so neither
        // narrows beyond t's own membership; every existing value in both stays supported.
        Variable<Integer> a = F.create("a_mv_cov3"), b = F.create("b_mv_cov3"), t = F.create("t_mv_cov3");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(1, 9), b, DiscreteDomain.of(2, 9), t, DiscreteDomain.of(9));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_eq_discreteDomain_coverage_soleCovererWithNoOtherReacher_onlyTargetValueSurvives() {
        // a={2,9} is the sole coverer of 9, and no other variable can reach ANY tSet value once a
        // is excluded (b's domain {1} doesn't intersect tSet={9} at all) -- a's threshold falls to
        // NEGATIVE_INFINITY, leaving only a's own direct membership in tSet (value 9) supported.
        Variable<Integer> a = F.create("a_mv_cov4"), b = F.create("b_mv_cov4"), t = F.create("t_mv_cov4");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 9), b, DiscreteDomain.of(1), t, DiscreteDomain.of(9));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains).orElseThrow();
        assertThat(result.get(a)).isEqualTo(DiscreteDomain.of(9));
        assertThat(result).doesNotContainKey(b);
        assertThat(result).doesNotContainKey(t);
    }

    @Test void propagate_eq_maxedVariableContinuous_skipsCoveragePath_usesBoundsConsistency() {
        // T is discrete but X is continuous -- still not every domain is discrete, so this stays
        // on the old bounds-only path (a distinct sub-branch from the target-continuous case above).
        Variable<Double> x = F.create("x_mv_cov5"), y = F.create("y_mv_cov5"), t = F.create("t_mv_cov5");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntervalDomain.of(3, 10), y, IntervalDomain.of(4, 8), t, DiscreteDomain.of(4.0, 10.0, 20.0));
        var result = MaxVariableConstraint.of(Set.of(x, y), Operator.EQ, t).propagate(domains).orElseThrow();
        assertThat(result.get(t)).isEqualTo(DiscreteDomain.of(4.0, 10.0));
    }

    @Test void propagate_eq_maxedVariableContinuous_boundsOnlyPath_discreteGapForcesEmpty() {
        // Mirrors propagate_eq_discreteDomain_infeasible_noValueEqualsTarget, but with the second
        // maxed variable continuous so the whole call stays on the old bounds-only path (a's own
        // domain still has a gap at 3): upper-clip forces a's tracked max down to t's bound (3),
        // then the sole-reacher lower-force pass tries to narrow a to exactly [3,3] -- empty, since
        // 3 isn't actually present in a's discrete domain.
        Variable<Double> a = F.create("a_mv_cov6"), x = F.create("x_mv_cov6"), t = F.create("t_mv_cov6");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(0.0, 1.0, 2.0, 4.0), x, IntervalDomain.of(0, 2), t, DiscreteDomain.of(3.0));
        assertThat(MaxVariableConstraint.of(Set.of(a, x), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    @Test void propagate_eq_discreteDomain_coverage_infeasible_emptyAchievableTargetSet() {
        // a,b both even-valued {2,4,6}; target {3,5} -- bounds alone say feasible (mLo=2<=tHi=5,
        // mHi=6>=tLo=3), but neither 3 nor 5 is literally achievable by any variable (both only
        // ever produce even maxima), so the achievable target set (tSet) is empty.
        Variable<Integer> a = F.create("a_mv_cov7"), b = F.create("b_mv_cov7"), t = F.create("t_mv_cov7");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 4, 6), b, DiscreteDomain.of(2, 4, 6), t, DiscreteDomain.of(3, 5));
        assertThat(MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = of(Operator.LEQ).propagateWithReasons(domains(0, 10, 0, 8, 0, 20));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void explainInfeasible_allSingleton_attributesAll() {
        Variable<Integer> a = F.create("a_mv_r1"), b = F.create("b_mv_r1"), t = F.create("t_mv_r1");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1000, 1000), b, IntRangeDomain.of(1, 1), t, IntRangeDomain.of(0, 0));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.LEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                a, IntervalDomain.of(1000, 1000), b, IntervalDomain.of(1, 1), t, IntervalDomain.of(0, 0))));
    }

    @Test void explainInfeasible_notAllSingleton_citesCurrentBounds() {
        Variable<Integer> a = F.create("a_mv_r2"), b = F.create("b_mv_r2"), t = F.create("t_mv_r2");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(500, 1000), b, IntRangeDomain.of(1, 3), t, IntRangeDomain.of(0, 0));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.LEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                a, IntervalDomain.of(500, 1000), b, IntervalDomain.of(1, 3), t, IntervalDomain.of(0, 0))));
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_citesExactValueSet() {
        Variable<Integer> a = F.create("a_mv_r3"), b = F.create("b_mv_r3"), t = F.create("t_mv_r3");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(1000, 1000), b, DiscreteDomain.of(1, 3), t, IntRangeDomain.of(0, 0));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.LEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                a, Set.of(1000), b, Set.of(1, 3), t, Set.of(0))));
    }

    @Test void explainInfeasible_eqCoverage_emptyAchievableTargetSet_citesExactValueSet() {
        // Same scenario as propagate_eq_discreteDomain_coverage_infeasible_emptyAchievableTargetSet:
        // bounds alone look feasible, so RangeNogoodConstraint#fromCurrentBounds can't explain this
        // (and wouldn't be sound here even if it tried -- neither a nor b is gapless anyway). Must
        // fall through to citing every variable's exact current value set instead of Optional.empty().
        Variable<Integer> a = F.create("a_mv_cov7r"), b = F.create("b_mv_cov7r"), t = F.create("t_mv_cov7r");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 4, 6), b, DiscreteDomain.of(2, 4, 6), t, DiscreteDomain.of(3, 5));
        var result = MaxVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(ValueSetNogoodConstraint.of(Map.of(
                a, Set.of(2, 4, 6), b, Set.of(2, 4, 6), t, Set.of(3, 5))));
    }
}
