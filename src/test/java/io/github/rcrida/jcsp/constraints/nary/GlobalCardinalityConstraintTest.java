package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
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
public class GlobalCardinalityConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Color> v4;

    // 4 vars: exactly 2 RED, 1 GREEN, 1 BLUE
    GlobalCardinalityConstraint<Color> constraint;

    @BeforeEach
    void setUp() {
        constraint = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1));
    }

    @Test
    void exactCounts_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void wrongCount_notSatisfied() {
        // 3 RED, 1 GREEN — but BLUE count is 0, not 1
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED, v4, Color.GREEN)))).isFalse();
    }

    @Test
    void countExceeded_notSatisfied() {
        // 3 RED already exceeds 2 — fails even with one variable unassigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED)))).isFalse();
    }

    @Test
    void partialAssignment_belowLimit_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED)))).isTrue();
    }

    @Test
    void openGcc_underCount_notSatisfied() {
        // Only RED and GREEN are tracked (BLUE is free / unconstrained).
        // With 1 RED assigned and 2 required, no value exceeds its limit mid-assignment,
        // so early failure doesn't fire — the mismatch is only detected when all vars assigned.
        var openGcc = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 2));
        assertThat(openGcc.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.GREEN, v3, Color.BLUE, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString())
                .isEqualTo("<(v1, v2, v3, v4), GlobalCardinality({BLUE=1, GREEN=1, RED=2})>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1)))
                .isEqualTo(constraint);
    }

    @Test
    void solver_exactDistribution() {
        // 4 vars over {RED, GREEN, BLUE}: exactly 2 RED, 1 GREEN, 1 BLUE.
        // Solutions: C(4,2) × C(2,1) × 1 = 6 × 2 = 12.
        Variable<Color> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Color> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Color> x3 = Variable.Factory.INSTANCE.create("x3");
        Variable<Color> x4 = Variable.Factory.INSTANCE.create("x4");
        var domain = EnumDomain.allOf(Color.class);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain)
                .variableDomain(x3, domain).variableDomain(x4, domain)
                .globalCardinalityConstraint(
                        Set.of(x1, x2, x3, x4),
                        Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(12);
    }
}
