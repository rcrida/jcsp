package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class CountConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Color> v4;

    CountConstraint<Color> eq2;
    CountConstraint<Color> leq1;
    CountConstraint<Color> geq2;

    @BeforeEach
    void setUp() {
        eq2  = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.EQ,  2);
        leq1 = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.LEQ, 1);
        geq2 = CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.GEQ, 2);
    }

    @Test
    void countEqualsN_satisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void countBelowN_notSatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void countAboveN_notSatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.RED, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void leq_countAtBound_satisfied() {
        assertThat(leq1.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void leq_countAboveBound_notSatisfied() {
        assertThat(leq1.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void geq_countAtBound_satisfied() {
        assertThat(geq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void geq_countBelowBound_notSatisfied() {
        assertThat(geq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
        assertThat(eq2.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(eq2.toString()).isEqualTo("<(v1, v2, v3, v4), count(RED) == 2>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(CountConstraint.of(Set.of(v1, v2, v3, v4), Color.RED, Operator.EQ, 2)).isEqualTo(eq2);
    }

    @Test
    void solver_countConstraint_correctSolutionCount() {
        // 4 variables over {RED, GREEN, BLUE}, exactly 2 must be RED.
        // Solutions: C(4,2) positions for RED × 2^2 choices for remaining = 6 × 4 = 24.
        Variable.Factory F = Variable.Factory.INSTANCE;
        Variable<Color> x1 = F.create("x1");
        Variable<Color> x2 = F.create("x2");
        Variable<Color> x3 = F.create("x3");
        Variable<Color> x4 = F.create("x4");
        var domain = EnumDomain.allOf(Color.class);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain)
                .variableDomain(x3, domain).variableDomain(x4, domain)
                .countConstraint(Set.of(x1, x2, x3, x4), Color.RED, Operator.EQ, 2)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(24);
    }
}
