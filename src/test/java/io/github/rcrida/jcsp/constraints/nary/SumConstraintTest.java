package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
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

    @Test
    @SuppressWarnings("unchecked")
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
