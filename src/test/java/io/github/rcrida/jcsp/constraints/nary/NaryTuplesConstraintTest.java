package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NaryTuplesConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> x = F.create("x");
    Variable<Integer> y = F.create("y");
    Variable<Integer> z = F.create("z");

    // cyclic permutations of (1, 2, 3)
    Assignment t1 = Assignment.of(Map.of(x, 1, y, 2, z, 3));
    Assignment t2 = Assignment.of(Map.of(x, 2, y, 3, z, 1));
    Assignment t3 = Assignment.of(Map.of(x, 3, y, 1, z, 2));

    NaryTuplesConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = NaryTuplesConstraint.of(Set.of(t1, t2, t3));
    }

    @Test
    void matchingTuple_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 2, z, 3)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 3, z, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 1, z, 2)))).isTrue();
    }

    @Test
    void nonMatchingTuple_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1, z, 1)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 3, z, 2)))).isFalse();
    }

    @Test
    void partialAssignment_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 2)))).isTrue();
    }

    @Test
    void of_mismatchedVariableSets_asserts() {
        var other = Assignment.of(Map.of(x, 1, y, 2)); // missing z
        assertThatThrownBy(() -> NaryTuplesConstraint.of(Set.of(t1, other)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_emptyTuples_asserts() {
        assertThatThrownBy(() -> NaryTuplesConstraint.of(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString())
                .isEqualTo("<(x, y, z), {{x=1, y=2, z=3}, {x=2, y=3, z=1}, {x=3, y=1, z=2}}>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(NaryTuplesConstraint.of(Set.of(t1, t2, t3))).isEqualTo(constraint);
    }

    @Test
    void solver_findsExactTupleSolutions() {
        // Domain is 1-3 for all variables; only cyclic permutations of (1,2,3) are allowed.
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .tuplesConstraint(Set.of(t1, t2, t3))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(3);
    }
}
