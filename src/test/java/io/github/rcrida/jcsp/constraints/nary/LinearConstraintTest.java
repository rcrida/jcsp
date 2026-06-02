package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinearConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x");
    Variable<Integer> y = F.create("y");

    // 2*x + 3*y == 12
    LinearConstraint<Integer> eq12;

    @BeforeEach
    void setUp() {
        eq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12);
    }

    @Test
    void weightedSum_satisfied() {
        // 2*0 + 3*4 = 12
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 4)))).isTrue();
        // 2*3 + 3*2 = 12
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 2)))).isTrue();
    }

    @Test
    void weightedSum_notSatisfied() {
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isFalse(); // 2+3=5
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 3)))).isFalse(); // 4+9=13
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq12.isSatisfiedBy(Assignment.of(Map.of(x, 3)))).isTrue();
    }

    @Test
    void leq_satisfied() {
        var leq12 = LinearConstraint.of(Map.of(x, 2, y, 3), Operator.LEQ, 12);
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 4)))).isTrue();
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isTrue();
        assertThat(leq12.isSatisfiedBy(Assignment.of(Map.of(x, 5, y, 1)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(eq12.toString()).isEqualTo("<(x, y), 2*x + 3*y == 12>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(LinearConstraint.of(Map.of(x, 2, y, 3), Operator.EQ, 12)).isEqualTo(eq12);
    }

    @Test
    void weightedSum_byte() {
        Variable<Byte> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, (byte) 2, b, (byte) 3), Operator.EQ, (byte) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 3, b, (byte) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (byte) 1, b, (byte) 1)))).isFalse();
    }

    @Test
    void weightedSum_short() {
        Variable<Short> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, (short) 2, b, (short) 3), Operator.EQ, (short) 12);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 3, b, (short) 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, (short) 1, b, (short) 1)))).isFalse();
    }

    @Test
    void weightedSum_long() {
        Variable<Long> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2L, b, 3L), Operator.EQ, 12L);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3L, b, 2L)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1L, b, 1L)))).isFalse();
    }

    @Test
    void weightedSum_float() {
        Variable<Float> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2.0f, b, 3.0f), Operator.EQ, 12.0f);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0f, b, 2.0f)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0f, b, 1.0f)))).isFalse();
    }

    @Test
    void weightedSum_double() {
        Variable<Double> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.of(Map.of(a, 2.0, b, 3.0), Operator.EQ, 12.0);
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 3.0, b, 2.0)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(a, 1.0, b, 1.0)))).isFalse();
    }

    @Test
    void weightedSum_unsupportedBoundType() {
        Variable<Number> a = F.create("a"), b = F.create("b");
        var c = LinearConstraint.<Number>builder()
                .variables(java.util.Set.of(a, b))
                .coefficients(Map.of(a, (Number) 2, b, (Number) 3))
                .bound(new AtomicInteger(12))
                .operator(Operator.EQ)
                .build();
        assertThatThrownBy(() -> c.isSatisfiedBy(Assignment.of(Map.of(a, 3, b, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported bound type");
    }

    @Test
    void solver_findsExactSolutions() {
        // 2*x + 3*y == 12, domain {0..4}: solutions are (0,4) and (3,2)
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(0, 4))
                .variableDomain(y, IntRangeDomain.of(0, 4))
                .linearConstraint(Map.of(x, 2, y, 3), Operator.EQ, 12)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(2);
    }
}
