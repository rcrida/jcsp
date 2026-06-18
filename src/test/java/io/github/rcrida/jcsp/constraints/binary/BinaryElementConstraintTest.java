package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryElementConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> INDEX = F.create("index");
    static final Variable<String> RESULT = F.create("result");
    static final List<String> ARRAY = List.of("alpha", "beta", "gamma");

    BinaryElementConstraint<String> constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryElementConstraint.of(INDEX, RESULT, ARRAY);
    }

    @Test
    void validIndex_matchingResult_satisfied() {
        assertThat(constraint.isSatisfiedBy(1, "alpha")).isTrue();
        assertThat(constraint.isSatisfiedBy(2, "beta")).isTrue();
        assertThat(constraint.isSatisfiedBy(3, "gamma")).isTrue();
    }

    @Test
    void validIndex_nonMatchingResult_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(1, "beta")).isFalse();
        assertThat(constraint.isSatisfiedBy(2, "gamma")).isFalse();
        assertThat(constraint.isSatisfiedBy(3, "alpha")).isFalse();
    }

    @Test
    void outOfBoundsIndex_notSatisfied() {
        assertThat(constraint.isSatisfiedBy(0, "alpha")).isFalse();
        assertThat(constraint.isSatisfiedBy(4, "alpha")).isFalse();
    }

    @Test
    void partialAssignment_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(INDEX, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(RESULT, "alpha")))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(index, result), result = [alpha, beta, gamma][index]>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(BinaryElementConstraint.of(INDEX, RESULT, ARRAY)).isEqualTo(constraint);
    }

    @Test
    void solver_elementConstraint_exactSolutions() {
        // result must equal the array value at the given index; each index maps to exactly one result.
        var resultDomain = DomainObjectSet.<String>builder().values(ARRAY).build();
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(INDEX, IntRangeDomain.of(1, 3))
                .variableDomain(RESULT, resultDomain)
                .elementConstraint(INDEX, RESULT, ARRAY)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(3);
    }
}
