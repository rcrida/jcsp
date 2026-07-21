package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SumVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> v1 = F.create("v1");
    Variable<Integer> v2 = F.create("v2");
    Variable<Integer> t = F.create("t");

    SumVariableConstraint<Integer> eq;

    @BeforeEach
    void setUp() {
        eq = SumVariableConstraint.of(Set.of(v1, v2), Operator.EQ, t);
    }

    // --- isSatisfiedBy() ---

    @Test
    void sumEqualsTarget_satisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 4, t, 7)))).isTrue();
    }

    @Test
    void sumMismatchesTarget_notSatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 4, t, 8)))).isFalse();
    }

    @Test
    void targetUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 4)))).isTrue();
    }

    @Test
    void summedVariableUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 3, t, 7)))).isTrue();
    }

    // --- propagate() ---

    @Test
    void propagate_wideDomains_noChange() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(0, 20));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_narrowsSummedVariableFromTarget() {
        // v1,v2 in [0,10]; t in [0,5] -> v1 (and v2) can be at most 5 each
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(0, 5));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(0, 5));
        assertThat(result.get().get(v2)).isEqualTo(IntRangeDomain.of(0, 5));
    }

    @Test
    void propagate_narrowsTargetFromSummedVariables() {
        // v1=3, v2=4 (both singleton) -> t forced to exactly 7, even though its domain was [0,20]
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(0, 20));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(7, 7));
    }

    @Test
    void propagate_infeasible_noOverlapPossible() {
        // v1,v2 in [0,2] each -> max sum 4, but t in [10,20] -> no overlap
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 2), v2, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(10, 20));
        assertThat(eq.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 10), t, IntRangeDomain.of(0, 20));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_allSingleton_attributesAll() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(10, 10));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, 3, v2, 4, t, 10)));
    }

    @Test
    void explainInfeasible_notAllSingleton_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(2, 5), t, IntRangeDomain.of(100, 100));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- toString() / of() ---

    @Test
    void testToString() {
        assertThat(eq.toString()).isEqualTo("<(t, v1, v2), v1 + v2 == t>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(SumVariableConstraint.of(Set.of(v1, v2), Operator.EQ, t)).isEqualTo(eq);
    }

    // --- numeric type dispatch (off target's assigned value) ---

    @Test
    void sum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.of(Set.of(a, b), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 1, b, (byte) 2, target, (byte) 3)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 2, b, (byte) 2, target, (byte) 3)))).isFalse();
    }

    @Test
    void sum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.of(Set.of(a, b), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 10, b, (short) 20, target, (short) 30)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 10, b, (short) 10, target, (short) 30)))).isFalse();
    }

    @Test
    void sum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.of(Set.of(a, b), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1L, b, 2L, target, 3L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2L, b, 2L, target, 3L)))).isFalse();
    }

    @Test
    void sum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.of(Set.of(a, b), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.5f, b, 1.5f, target, 3.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0f, b, 1.0f, target, 3.0f)))).isFalse();
    }

    @Test
    void sum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.of(Set.of(a, b), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.5, b, 1.5, target, 3.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0, b, 1.0, target, 3.0)))).isFalse();
    }

    @Test
    void sum_unsupportedTargetType() {
        Variable<Number> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = SumVariableConstraint.<Number>builder()
                .variables(Set.of(a, b, target))
                .summedVariables(Set.of(a, b))
                .target(target)
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, 1, b, 2, target, new AtomicInteger(3)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported type");
    }
}
