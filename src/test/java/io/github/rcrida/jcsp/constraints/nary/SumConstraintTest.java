package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
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
