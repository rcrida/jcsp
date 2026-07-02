package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SumConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> v1 = F.create("v1");
    Variable<Integer> v2 = F.create("v2");
    Variable<Integer> v3 = F.create("v3");

    SumConstraint<Integer> eq10;
    SumConstraint<Integer> leq10;
    SumConstraint<Integer> geq10;

    @BeforeEach
    void setUp() {
        eq10  = SumConstraint.of(Set.of(v1, v2, v3), Operator.EQ,  10);
        leq10 = SumConstraint.of(Set.of(v1, v2, v3), Operator.LEQ, 10);
        geq10 = SumConstraint.of(Set.of(v1, v2, v3), Operator.GEQ, 10);
    }

    @Test
    void sumEqualsbound_satisfied() {
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 3, v3, 4)))).isTrue();
    }

    @Test
    void sumBelowBound_notSatisfied() {
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3)))).isFalse();
    }

    @Test
    void sumAboveBound_notSatisfied() {
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 5, v3, 5)))).isFalse();
    }

    @Test
    void leq_sumAtBound_satisfied() {
        assertThat(leq10.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 3, v3, 4)))).isTrue();
    }

    @Test
    void leq_sumBelowBound_satisfied() {
        assertThat(leq10.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3)))).isTrue();
    }

    @Test
    void leq_sumAboveBound_notSatisfied() {
        assertThat(leq10.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 5, v3, 5)))).isFalse();
    }

    @Test
    void geq_sumAtBound_satisfied() {
        assertThat(geq10.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 3, v3, 4)))).isTrue();
    }

    @Test
    void geq_sumAboveBound_satisfied() {
        assertThat(geq10.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 5, v3, 5)))).isTrue();
    }

    @Test
    void geq_sumBelowBound_notSatisfied() {
        assertThat(geq10.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3)))).isFalse();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of(v1, 5)))).isTrue();
        assertThat(eq10.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 5)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(eq10.toString()).isEqualTo("<(v1, v2, v3), v1 + v2 + v3 == 10>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(SumConstraint.of(Set.of(v1, v2, v3), Operator.EQ, 10)).isEqualTo(eq10);
    }

    @Test
    void sum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.of(Set.of(a, b), Operator.EQ, (byte) 3);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 1, b, (byte) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 2, b, (byte) 2)))).isFalse();
    }

    @Test
    void sum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.of(Set.of(a, b), Operator.EQ, (short) 30);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 10, b, (short) 20)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 10, b, (short) 10)))).isFalse();
    }

    @Test
    void sum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.of(Set.of(a, b), Operator.EQ, 3L);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1L, b, 2L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2L, b, 2L)))).isFalse();
    }

    @Test
    void sum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.of(Set.of(a, b), Operator.EQ, 3.0f);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.5f, b, 1.5f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0f, b, 1.0f)))).isFalse();
    }

    @Test
    void sum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.of(Set.of(a, b), Operator.EQ, 3.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.5, b, 1.5)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0, b, 1.0)))).isFalse();
    }

    // --- propagate() ---

    @Test
    void propagate_eq_tightensBothBounds() {
        // v1∈{1..9}, v2∈{1..9}, v3∈{1..9}, sum = 15
        // new_max(v1) = 15 - min(v2) - min(v3) = 15 - 1 - 1 = 13 → capped at 9 (no change)
        // After assigning v2=8, v3=6: new_max(v1) = 15-8-6 = 1, new_min(v1) = 15-8-6 = 1 → v1 = {1}
        Variable<Integer> v3 = F.create("v3");
        var c = SumConstraint.of(Set.of(v1, v2, v3), Operator.EQ, 15);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(8, 8),
                v3, IntRangeDomain.of(6, 6));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(v1);
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(1, 1));
    }

    @Test
    void propagate_leq_tightensUpperBound() {
        // v1∈{1..9}, v2∈{5..5}: sum ≤ 8 → new_max(v1) = 8 - 5 = 3
        var c = SumConstraint.of(Set.of(v1, v2), Operator.LEQ, 8);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(5, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(v1);
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(1, 3));
    }

    @Test
    void propagate_geq_tightensLowerBound() {
        // v1∈{1..9}, v2∈{5..5}: sum ≥ 8 → new_min(v1) = 8 - 5 = 3
        var c = SumConstraint.of(Set.of(v1, v2), Operator.GEQ, 8);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(5, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey(v1);
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(3, 9));
    }

    @Test
    void propagate_otherOperator_returnsNoChange() {
        var c = SumConstraint.of(Set.of(v1, v2), Operator.NEQ, 5);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(1, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_eq_infeasible_kAboveMax() {
        // v1∈{1..3}, v2∈{1..3}: max sum = 6 < 10 → infeasible
        var c = SumConstraint.of(Set.of(v1, v2), Operator.EQ, 10);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 3),
                v2, IntRangeDomain.of(1, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_leq_infeasible() {
        // v1∈{5..9}, v2∈{5..9}: min sum = 10 > 8 → infeasible for LEQ
        var c = SumConstraint.of(Set.of(v1, v2), Operator.LEQ, 8);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(5, 9),
                v2, IntRangeDomain.of(5, 9));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_geq_infeasible() {
        // v1∈{1..3}, v2∈{1..3}: max sum = 6 < 10 → infeasible for GEQ
        var c = SumConstraint.of(Set.of(v1, v2), Operator.GEQ, 10);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 3),
                v2, IntRangeDomain.of(1, 3));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // Wide domains — no pruning possible
        var c = SumConstraint.of(Set.of(v1, v2), Operator.EQ, 10);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(1, 9));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate() : Double / IntervalDomain ---

    @Test
    void propagateDouble_eq_tightensIntervalBounds() {
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        Variable<Double> d3 = F.create("d3");
        var c = SumConstraint.of(Set.of(d1, d2, d3), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 10.0),
                d2, IntervalDomain.of(3.0, 3.0),
                d3, IntervalDomain.of(0.0, 10.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(d1)).isEqualTo(IntervalDomain.of(0.0, 7.0));
        assertThat(result.get().get(d3)).isEqualTo(IntervalDomain.of(0.0, 7.0));
        assertThat(result.get()).doesNotContainKey(d2);
    }

    @Test
    void propagateDouble_leq_tightensUpperBound() {
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.LEQ, 8.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 10.0),
                d2, IntervalDomain.of(5.0, 5.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(d1)).isEqualTo(IntervalDomain.of(0.0, 3.0));
    }

    @Test
    void propagateDouble_geq_tightensLowerBound() {
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.GEQ, 8.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 10.0),
                d2, IntervalDomain.of(5.0, 5.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(d1)).isEqualTo(IntervalDomain.of(3.0, 10.0));
    }

    @Test
    void propagateDouble_infeasible_kAboveMax() {
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 3.0),
                d2, IntervalDomain.of(0.0, 3.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_eq_infeasible_kBelowMin() {
        // d1,d2 ∈ [5,9]: min sum = 10 > 8 → infeasible for EQ
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.EQ, 8.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(5.0, 9.0),
                d2, IntervalDomain.of(5.0, 9.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_leq_infeasible() {
        // d1,d2 ∈ [5,9]: min sum = 10 > 8 → infeasible for LEQ
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.LEQ, 8.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(5.0, 9.0),
                d2, IntervalDomain.of(5.0, 9.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_geq_infeasible() {
        // d1,d2 ∈ [0,3]: max sum = 6 < 10 → infeasible for GEQ
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.GEQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 3.0),
                d2, IntervalDomain.of(0.0, 3.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateDouble_noChange_returnsEmptyMap() {
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 10.0),
                d2, IntervalDomain.of(0.0, 10.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagateDouble_mixedIntervalAndEnumerableOperands() {
        // d1 is an IntervalDomain, d2 is a plain enumerable Domain<Double>; d1 + d2 == 10
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, IntervalDomain.of(0.0, 10.0),
                d2, io.github.rcrida.jcsp.domains.DomainObjectSet.<Double>builder().value(2.0).value(8.0).build());
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        // newMin(d1) = 10 - max(d2) = 10 - 8 = 2; newMax(d1) = 10 - min(d2) = 10 - 2 = 8
        assertThat(result.get().get(d1)).isEqualTo(IntervalDomain.of(2.0, 8.0));
        assertThat(result.get()).doesNotContainKey(d2);
    }

    @Test
    void propagateDouble_enumerableOperandPrunedToEmpty_infeasible() {
        // d1 enumerable {0.0, 1.0}, d2∈[9.2, 9.8]; d1 + d2 == 10
        // Globally feasible (totalMin=9.2, totalMax=10.8), but d1 must narrow to [0.2, 0.8],
        // which excludes both 0.0 and 1.0 → infeasible per-variable.
        Variable<Double> d1 = F.create("d1");
        Variable<Double> d2 = F.create("d2");
        var c = SumConstraint.of(Set.of(d1, d2), Operator.EQ, 10.0);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                d1, io.github.rcrida.jcsp.domains.DomainObjectSet.<Double>builder().value(0.0).value(1.0).build(),
                d2, IntervalDomain.of(9.2, 9.8));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagateFloat_eq_tightensEnumerableDomain() {
        // f1 ∈ {0..9} (Float), f2 fixed at 5.0f; f1 + f2 == 8.0f → f1 narrowed to {3.0f}
        Variable<Float> f1 = F.create("f1");
        Variable<Float> f2 = F.create("f2");
        var c = SumConstraint.of(Set.of(f1, f2), Operator.EQ, 8.0f);
        var f1Domain = io.github.rcrida.jcsp.domains.DomainObjectSet.<Float>builder();
        for (float v = 0f; v <= 9f; v++) f1Domain.value(v);
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                f1, f1Domain.build(),
                f2, io.github.rcrida.jcsp.domains.DomainObjectSet.<Float>builder().value(5.0f).build());
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        DiscreteDomain<Float> f1Result = (DiscreteDomain<Float>) result.get().get(f1);
        assertThat(f1Result.toList()).containsExactly(3.0f);
    }

    // --- propagateWithReasons() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 9),
                v2, IntRangeDomain.of(1, 9),
                v3, IntRangeDomain.of(1, 9));
        var result = eq10.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void propagateWithReasons_allSingleton_infeasible_attributesAll() {
        // v1=v2=v3=3 (all singleton), sum=9 < 10 → infeasible; every value is a concrete fact,
        // so the full set is a sound, self-contained explanation.
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3),
                v2, IntRangeDomain.of(3, 3),
                v3, IntRangeDomain.of(3, 3));
        var result = eq10.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).containsOnly(Map.entry(v1, 3), Map.entry(v2, 3), Map.entry(v3, 3));
    }

    @Test
    void propagateWithReasons_notAllSingleton_initialCheckInfeasible_returnsEmptyReason() {
        // v1,v2∈{1..3}, EQ 10: max sum = 6 < 10 → infeasible, but neither is pinned, so an
        // unlisted open-domain variable can't be ruled out — falls back to empty.
        var c = SumConstraint.of(Set.of(v1, v2), Operator.EQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(1, 3),
                v2, IntRangeDomain.of(1, 3));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void propagateWithReasons_onePinned_perVariablePrunedToEmpty_returnsEmptyReason() {
        // v1 enumerable {0,1}, v2∈{9..9} (singleton); v1 + v2 == 10.
        // Globally feasible (totalMin=9, totalMax=10), but v1 must narrow to {1}, which is
        // present — use a domain where the required value is absent instead.
        var c = SumConstraint.of(Set.of(v1, v2), Operator.EQ, 10);
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, io.github.rcrida.jcsp.domains.DomainObjectSet.<Integer>builder().value(0).value(4).build(),
                v2, IntRangeDomain.of(9, 9));
        // v1 must equal 1 (10-9), which is absent from {0,4} → infeasible; v1 has no singleton
        // value to blame, so even though v2 is pinned, the explanation can't be sound without v1.
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void sum_unsupportedBoundType() {
        Variable<Number> a = F.create("a"), b = F.create("b");
        var c = SumConstraint.<Number>builder()
                .variables(Set.of(a, b))
                .bound(new AtomicInteger(3))
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported bound type");
    }
}
