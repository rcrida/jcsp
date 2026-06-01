package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
}
