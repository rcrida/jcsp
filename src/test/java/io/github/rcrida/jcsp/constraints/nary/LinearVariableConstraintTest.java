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

public class LinearVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> v1 = F.create("v1");
    Variable<Integer> v2 = F.create("v2");
    Variable<Integer> t = F.create("t");

    // 2*v1 + 3*v2 == t
    LinearVariableConstraint<Integer> eq;

    @BeforeEach
    void setUp() {
        eq = LinearVariableConstraint.of(Map.of(v1, 2, v2, 3), Operator.EQ, t);
    }

    // --- isSatisfiedBy() ---

    @Test
    void weightedSumEqualsTarget_satisfied() {
        // 2*2 + 3*1 = 7
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 2, v2, 1, t, 7)))).isTrue();
    }

    @Test
    void weightedSumMismatchesTarget_notSatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 2, v2, 1, t, 8)))).isFalse();
    }

    @Test
    void targetUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 2, v2, 1)))).isTrue();
    }

    @Test
    void coefficientVariableUnassigned_optimisticallySatisfied() {
        assertThat(eq.isSatisfiedBy(Assignment.of(Map.of(v1, 2, t, 7)))).isTrue();
    }

    // --- propagate() ---

    @Test
    void propagate_narrowsVariableFromTargetAndOtherVariable() {
        // v1 in [0,10] (open); v2={4} (singleton, contributes 3*4=12); target={20} (singleton)
        // -> 2*v1 + 12 == 20 -> v1 forced to exactly 4
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(20, 20));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v1)).isEqualTo(IntRangeDomain.of(4, 4));
        assertThat(result.get()).doesNotContainKey(v2);
        assertThat(result.get()).doesNotContainKey(t);
    }

    @Test
    void propagate_narrowsTargetFromVariables() {
        // v1={3}, v2={4} (both singleton) -> 2*3 + 3*4 = 18 -> target forced to exactly 18
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(0, 50));
        var result = eq.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(t)).isEqualTo(IntRangeDomain.of(18, 18));
    }

    @Test
    void propagate_infeasible_noOverlapPossible() {
        // 2*v1+3*v2 maxes out at 2*2+3*2=10, but target starts at 100
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 2), v2, IntRangeDomain.of(0, 2), t, IntRangeDomain.of(100, 200));
        assertThat(eq.propagate(domains)).isEmpty();
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(20, 20));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void explainInfeasible_allSingleton_attributesAll() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(4, 4), t, IntRangeDomain.of(100, 100));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, 3, v2, 4, t, 100)));
    }

    @Test
    void explainInfeasible_notAllSingleton_returnsEmptyReason() {
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, IntRangeDomain.of(3, 3), v2, IntRangeDomain.of(2, 10), t, IntRangeDomain.of(1000, 1000));
        var result = eq.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    // --- toString() / of() ---

    @Test
    void testToString() {
        assertThat(eq.toString()).isEqualTo("<(t, v1, v2), 2*v1 + 3*v2 == t>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LinearVariableConstraint.of(Map.of(v1, 2, v2, 3), Operator.EQ, t)).isEqualTo(eq);
    }

    // --- numeric type dispatch (off target's assigned value) ---

    @Test
    void weightedSum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 2, b, (byte) 1, target, (byte) 7)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 2, b, (byte) 1, target, (byte) 8)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 2, b, (short) 1, target, (short) 7)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 2, b, (short) 1, target, (short) 8)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2L, b, 1L, target, 7L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2L, b, 1L, target, 8L)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2.0f, b, 1.0f, target, 7.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2.0f, b, 1.0f, target, 8.0f)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, target);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2.0, b, 1.0, target, 7.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 2.0, b, 1.0, target, 8.0)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedTargetType() {
        Variable<Number> a = F.create("a"), b = F.create("b"), target = F.create("t");
        var c = LinearVariableConstraint.<Number>builder()
                .variables(Set.of(a, b, target))
                .coefficients(Map.<Variable<Number>, Number>of(a, 2, b, 3))
                .target(target)
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, 2, b, 1, target, new AtomicInteger(7)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported type");
    }
}
