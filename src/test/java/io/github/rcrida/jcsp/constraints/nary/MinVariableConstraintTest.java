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

public class MinVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Double> X = F.create("x_mnv");
    static final Variable<Double> Y = F.create("y_mnv");
    static final Variable<Double> T = F.create("t_mnv");

    static MinVariableConstraint<Double> of(Operator operator) {
        return MinVariableConstraint.of(Set.of(X, Y), operator, T);
    }

    static Map<Variable<?>, Domain<?>> domains(double xLo, double xHi, double yLo, double yHi, double tLo, double tHi) {
        return Map.of(X, IntervalDomain.of(xLo, xHi), Y, IntervalDomain.of(yLo, yHi), T, IntervalDomain.of(tLo, tHi));
    }

    static IntervalDomain xDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(X); }
    static IntervalDomain yDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(Y); }
    static IntervalDomain tDom(Map<Variable<?>, Domain<?>> m) { return (IntervalDomain) m.get(T); }

    // --- isSatisfiedBy ---

    @Test void isSatisfiedBy_eq_satisfied() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 7.0, T, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_eq_violated() {
        assertThat(of(Operator.EQ).isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 6.0, T, 7.0)))).isFalse();
    }

    @Test void isSatisfiedBy_leq_satisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 3.0, Y, 7.0, T, 7.0)))).isTrue();
    }

    @Test void isSatisfiedBy_leq_violated() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 8.0, Y, 9.0, T, 7.0)))).isFalse();
    }

    @Test void isSatisfiedBy_geq_satisfied() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(X, 5.0, Y, 8.0, T, 5.0)))).isTrue();
    }

    @Test void isSatisfiedBy_geq_violated() {
        assertThat(of(Operator.GEQ).isSatisfiedBy(Assignment.of(Map.of(X, 2.0, Y, 4.0, T, 5.0)))).isFalse();
    }

    @Test void isSatisfiedBy_targetUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 10.0, Y, 3.0)))).isTrue();
    }

    @Test void isSatisfiedBy_minedVariableUnassigned_optimisticallySatisfied() {
        assertThat(of(Operator.LEQ).isSatisfiedBy(Assignment.of(Map.of(X, 10.0, T, 3.0)))).isTrue();
    }

    // --- toString / of() ---

    @Test void testToString() {
        assertThat(of(Operator.LEQ).toString()).isEqualTo("<(t_mnv, x_mnv, y_mnv), min(x_mnv, y_mnv) <= t_mnv>");
    }

    @Test void of_createsEquivalentConstraint() {
        assertThat(MinVariableConstraint.of(Set.of(X, Y), Operator.LEQ, T)).isEqualTo(of(Operator.LEQ));
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

    // --- propagate: GEQ (min(vars) >= target) ---

    @Test void propagate_geq_wideDomains_noChange() {
        var result = of(Operator.GEQ).propagate(domains(5, 10, 5, 10, 0, 3)).orElseThrow();
        assertThat(result).isEmpty();
    }

    @Test void propagate_geq_raisesVarsLowerBoundFromTarget() {
        // X,Y in [0,10]; T in [3,20]: both vars' min raised to target's current min (3)
        var result = of(Operator.GEQ).propagate(domains(0, 10, 0, 10, 3, 20)).orElseThrow();
        assertThat(xDom(result).getMin()).isEqualTo(3.0);
        assertThat(yDom(result).getMin()).isEqualTo(3.0);
    }

    @Test void propagate_geq_lowersTargetUpperBoundFromVars_lowerUntouched() {
        // Achievable min(vars) tops out at mHi = min_i(maxs[i]) = min(10,9) = 9 (every variable
        // pinned to its own max) -- so GEQ (min(vars) >= target) needs target <= 9: T's upper
        // bound lowers to 9. GEQ never restricts how low target can go, so T's lower bound (0)
        // is untouched -- that's LEQ's job.
        var result = of(Operator.GEQ).propagate(domains(4, 10, 8, 9, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMax()).isEqualTo(9.0);
        assertThat(tDom(result).getMin()).isEqualTo(0.0);
    }

    @Test void propagate_geq_infeasible_noVarCanReachTargetMin() {
        // X in [0,3], Y in [0,4]: both vars' own max tops out below T's min (5) -> infeasible
        assertThat(of(Operator.GEQ).propagate(domains(0, 3, 0, 4, 5, 10))).isEmpty();
    }

    // --- propagate: LEQ (min(vars) <= target) ---

    @Test void propagate_leq_raisesTargetLowerBoundFromVars_upperUntouched() {
        // Achievable min(vars) bottoms out at mLo = min_i(mins[i]) = min(3,4) = 3 (the variable
        // with the smallest own minimum determines it) -- so LEQ (min(vars) <= target) needs
        // target >= 3: T's lower bound raises to 3. LEQ never restricts how high target can go,
        // so T's upper bound (20) is untouched -- that's GEQ's job.
        var result = of(Operator.LEQ).propagate(domains(3, 10, 4, 9, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(3.0);
        assertThat(tDom(result).getMax()).isEqualTo(20.0);
    }

    @Test void propagate_leq_forcesMaxDownWhenOnlyOneReaches() {
        // X in [0,10], Y in [7,10] -> only X can reach down to T's max (5); X's max lowered to 5
        var result = of(Operator.LEQ).propagate(domains(0, 10, 7, 10, 0, 5)).orElseThrow();
        assertThat(xDom(result).getMax()).isEqualTo(5.0);
        assertThat(result).doesNotContainKey(Y);
    }

    @Test void propagate_leq_infeasible_noVarCanReachTargetMax() {
        // X in [8,10], Y in [7,9]: both vars' own min already exceeds T's max (5) -> infeasible
        assertThat(of(Operator.LEQ).propagate(domains(8, 10, 7, 9, 0, 5))).isEmpty();
    }

    // --- propagate: EQ (both directions) ---

    @Test void propagate_eq_narrowsTargetBothDirections() {
        // X in [3,10], Y in [4,9] -> mLo=3, mHi=9: LEQ's half raises T's lower bound to 3, GEQ's
        // half lowers T's upper bound to 9 -> T narrows to [3,9].
        var result = of(Operator.EQ).propagate(domains(3, 10, 4, 9, 0, 20)).orElseThrow();
        assertThat(tDom(result).getMin()).isEqualTo(3.0);
        assertThat(tDom(result).getMax()).isEqualTo(9.0);
    }

    @Test void propagate_eq_infeasible_noVarCanReachTargetMin() {
        assertThat(of(Operator.EQ).propagate(domains(0, 3, 0, 4, 5, 10))).isEmpty();
    }

    @Test void propagate_eq_infeasible_noVarCanReachTargetMax() {
        assertThat(of(Operator.EQ).propagate(domains(8, 10, 7, 9, 0, 5))).isEmpty();
    }

    // --- propagate: discrete domain gap forces infeasibility in the force pass ---

    @Test void propagate_eq_discreteDomain_infeasible_noValueEqualsTarget() {
        // a: {2,3,5,6} (gap at 4), b: [5,8], t: [4,4] (singleton). b's own min (5) already exceeds
        // t (4), so b alone can't reach down to 4 either -- a is the sole variable that could
        // still supply min(vars)=4, but 4 itself is missing from a's domain: no discrete value
        // anywhere satisfies min(a,b)==4.
        Variable<Integer> a = F.create("a_mnv_gap"), b = F.create("b_mnv_gap"), t = F.create("t_mnv_gap");
        var domains = Map.<Variable<?>, Domain<?>>of(
                a, DiscreteDomain.of(2, 3, 5, 6), b, IntRangeDomain.of(5, 8), t, IntRangeDomain.of(4, 4));
        assertThat(MinVariableConstraint.of(Set.of(a, b), Operator.EQ, t).propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test void propagateWithReasons_feasible_returnsEmptyReason() {
        var result = of(Operator.GEQ).propagateWithReasons(domains(0, 10, 2, 8, 0, 3));
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test void explainInfeasible_allSingleton_attributesAll() {
        Variable<Integer> a = F.create("a_mnv_r1"), b = F.create("b_mnv_r1"), t = F.create("t_mnv_r1");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 0), b, IntRangeDomain.of(1, 1), t, IntRangeDomain.of(1000, 1000));
        var result = MinVariableConstraint.of(Set.of(a, b), Operator.GEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                a, IntervalDomain.of(0, 0), b, IntervalDomain.of(1, 1), t, IntervalDomain.of(1000, 1000))));
    }

    @Test void explainInfeasible_notAllSingleton_citesCurrentBounds() {
        Variable<Integer> a = F.create("a_mnv_r2"), b = F.create("b_mnv_r2"), t = F.create("t_mnv_r2");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 500), b, IntRangeDomain.of(1, 3), t, IntRangeDomain.of(1000, 1000));
        var result = MinVariableConstraint.of(Set.of(a, b), Operator.GEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(RangeNogoodConstraint.of(Map.of(
                a, IntervalDomain.of(0, 500), b, IntervalDomain.of(1, 3), t, IntervalDomain.of(1000, 1000))));
    }

    @Test void explainInfeasible_gappedNonSingletonDomain_fallsThroughToEmpty() {
        Variable<Integer> a = F.create("a_mnv_r3"), b = F.create("b_mnv_r3"), t = F.create("t_mnv_r3");
        var domains = Map.<Variable<?>, Domain<?>>of(a, IntRangeDomain.of(0, 0), b, DiscreteDomain.of(1, 3), t, IntRangeDomain.of(1000, 1000));
        var result = MinVariableConstraint.of(Set.of(a, b), Operator.GEQ, t).propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- ConstraintSatisfactionProblem.Builder#minConstraint(variable-target overload) ---

    @Test void builderMinConstraint_variableTarget_solverIntegration() {
        Variable<Integer> x1 = F.create("x1_mnv_b"), x2 = F.create("x2_mnv_b"), k = F.create("k_mnv_b");
        var domain = IntRangeDomain.of(0, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(k, domain)
                .minConstraint(Set.of(x1, x2), Operator.EQ, k)
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).isNotEmpty();
        solutions.forEach(a -> assertThat(a.getValue(k))
                .contains(Math.min(a.getValue(x1).orElseThrow(), a.getValue(x2).orElseThrow())));
    }
}
