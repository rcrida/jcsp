package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NumericBoundsTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> v1 = F.create("v1");
    Variable<Integer> v2 = F.create("v2");
    Variable<Integer> t = F.create("t");

    @Test
    void propagateWeightedSumVsTarget_uniformCoefficients_narrowsTargetFromVariables() {
        // v1=3, v2=4 (singleton), coefficients [1,1] -> target forced to exactly 7
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(0, 50));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(7, 7));
    }

    @Test
    void propagateWeightedSumVsTarget_negativeCoefficient_narrowsTargetFromVariables() {
        // v1=5, v2=2 (singleton), coefficients [1,-1] -> v1 - v2 == target -> target forced to 3
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(5, 5), v2, IntRangeDomain.of(2, 2), t, IntRangeDomain.of(-20, 20));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(3, 3));
    }

    @Test
    void propagateWeightedSumVsTarget_negativeCoefficient_narrowsVariableFromTarget() {
        // v1 open [0,10], v2=2 (singleton), target=3 (singleton), coefficients [1,-1]
        // -> v1 - 2 == 3 -> v1 forced to exactly 5
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(2, 2), t, IntRangeDomain.of(3, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(5, 5));
    }

    @Test
    void propagateWeightedSumVsTarget_leq_onlyNarrowsRelevantDirection() {
        // v1 <= target; v1 in [0,10], target in [0,3] -> v1 narrows to [0,3], target unaffected
        // (LEQ only constrains an upper bound on v1 and a lower bound on target, and target's
        // own lower bound already satisfies that trivially)
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(0, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1), new double[]{1.0}, t, Operator.LEQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(0, 3));
        assertThat(result.get()).doesNotContainKey(t);
    }

    @Test
    void propagateWeightedSumVsTarget_geq_narrowsVariableLowerBound() {
        // v1 >= target; v1 open [0,10], target=7 (singleton) -> v1's lower bound raised to 7,
        // upper bound left unconstrained by GEQ
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(7, 7));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1), new double[]{1.0}, t, Operator.GEQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(7, 10));
    }

    @Test
    void propagateWeightedSumVsTarget_geq_infeasible() {
        // v1 in [0,2] (max 2), target in [5,10] (min 5) -> v1 can never reach target's minimum
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(5, 10));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1), new double[]{1.0}, t, Operator.GEQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_leq_infeasible() {
        // v1=5 (singleton), target in [0,2] (max 2) -> v1 can never be <= target
        var domains = Map.<Variable<?>, Domain<?>>of(v1, IntRangeDomain.of(5, 5), t, IntRangeDomain.of(0, 2));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1), new double[]{1.0}, t, Operator.LEQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_eq_infeasible_targetTooLow() {
        // v1=5, v2=5 (min sum 10), target max 2 -> target can never reach the sum's minimum
        // (the opposite direction from noOverlapPossible, which has target too high)
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(5, 5), v2, IntRangeDomain.of(5, 5), t, IntRangeDomain.of(0, 2));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_infeasible_narrowedVariableDomainGapped() {
        // Overall aggregate range allows a solution (v1+v2 could be 3), but v1's own domain is
        // {0,10} (gapped, no 3) -- distinct infeasibility path from the aggregate check: the
        // per-variable narrowed range [3,3] simply doesn't overlap v1's actual domain values.
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, DomainObjectSet.<Integer>builder().value(0).value(10).build(),
                v2, IntRangeDomain.of(0, 0), t, IntRangeDomain.of(3, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_infeasible_narrowedTargetDomainGapped() {
        // v1=3, v2=0 (sum forced to 3), but target's domain is {0,10} (gapped, no 3) -- same
        // distinct infeasibility path as above, but for target's own narrowing instead.
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(0, 0),
                t, DomainObjectSet.<Integer>builder().value(0).value(10).build());
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_negativeCoefficientVariable_getsNarrowed() {
        // v1=5 (singleton), v2 open [0,10], target=3 (singleton), coefficients [1,-1]
        // -> 5 - v2 == 3 -> v2 forced to exactly 2 (the negative-coefficient variable itself
        // narrows here, unlike the other negative-coefficient tests above where only v1 or the
        // target narrowed)
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(5, 5), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(3, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(2, 2));
    }

    @Test
    void propagateWeightedSumVsTarget_geq_negativeCoefficientVariable_getsNarrowed() {
        // v1 - v2 >= target; v1=10 (singleton), v2 open [0,10], target=3 (singleton)
        // -> 10 - v2 >= 3 -> v2 <= 7
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(10, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(3, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.GEQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(0, 7));
    }

    @Test
    void propagateWeightedSumVsTarget_leq_negativeCoefficientVariable_getsNarrowed() {
        // v1 - v2 <= target; v1=10 (singleton), v2 open [0,10], target=3 (singleton)
        // -> 10 - v2 <= 3 -> v2 >= 7
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(10, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(3, 3));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.LEQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(7, 10));
    }

    @Test
    void propagateWeightedSumVsTarget_zeroCoefficient_variableUntouched() {
        // v1 has coefficient 0 -> contributes nothing and is never narrowed, regardless of target
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(5, 5));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{0.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(v1);
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(5, 5));
    }

    @Test
    void propagateWeightedSumVsTarget_infeasible_noOverlapPossible() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 2), v2, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(100, 200));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, -1.0}, t, Operator.EQ, domains);
        assertThat(result).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_nonPropagatingOperator_isNoOp() {
        // NEQ isn't one of {EQ, LEQ, GEQ} -- always a no-op, even for domains that would be
        // infeasible under EQ
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 2), v2, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(100, 200));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.NEQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagateWeightedSumVsTarget_wideDomains_noChange() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(0, 20));
        var result = NumericBounds.propagateWeightedSumVsTarget(
                List.of(v1, v2), new double[]{1.0, 1.0}, t, Operator.EQ, domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }
}
