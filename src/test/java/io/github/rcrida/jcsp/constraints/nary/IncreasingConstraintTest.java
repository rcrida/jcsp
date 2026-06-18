package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class IncreasingConstraintTest {
    @Mock Variable<Integer> v1;
    @Mock Variable<Integer> v2;
    @Mock Variable<Integer> v3;
    @Mock Variable<Integer> v4;

    IncreasingConstraint<Integer> constraint;

    @BeforeEach
    void setUp() {
        constraint = IncreasingConstraint.of(List.of(v1, v2, v3, v4));
    }

    @Test
    void nonDecreasing_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 2, v4, 5)))).isTrue();
    }

    @Test
    void strictlyIncreasing_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3, v4, 4)))).isTrue();
    }

    @Test
    void allEqual_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 3, v2, 3, v3, 3, v4, 3)))).isTrue();
    }

    @Test
    void decreasing_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 4, v2, 3, v3, 2, v4, 1)))).isFalse();
    }

    @Test
    void singleViolation_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 3, v3, 2, v4, 4)))).isFalse();
    }

    @Test
    void partialAssignment_satisfiedOptimistically() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 5)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v3, 3)))).isTrue();
    }

    @Test
    void partialAssignment_assignedPairViolated_notSatisfied() {
        // v1 and v2 are both assigned and v1 > v2 — caught even though v3 and v4 are unknown
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 2)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(v1, v2, v3, v4), increasing>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(IncreasingConstraint.of(List.of(v1, v2, v3, v4))).isEqualTo(constraint);
    }

    @Test
    void getAsBinaryConstraints_returnsLeqPairs() {
        assertThat(constraint.getAsBinaryConstraints()).hasSize(3); // one per consecutive pair
    }

    @Test
    void solver_nonDecreasingSequences() {
        // Count non-decreasing (v1 <= v2 <= v3) sequences over domain {1, 2, 3}.
        // Equivalent to combinations with repetition: C(3+3-1, 3) = C(5,3) = 10.
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        var domain = IntRangeDomain.of(1, 3);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .increasingConstraint(List.of(x1, x2, x3))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(10);
    }
}
