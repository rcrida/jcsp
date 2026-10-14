package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AssignmentVariableConsistencyConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static final Variable<Assignment> V1 = VARIABLE_FACTORY.create("V1");
    static final Variable<Assignment> V2 = VARIABLE_FACTORY.create("V2");
    static final Variable<Assignment> V3 = VARIABLE_FACTORY.create("V3");
    static final Variable<Object> CLIQUE_VARIABLE = VARIABLE_FACTORY.create("CV1");
    static final Variable<Object> ANOTHER_CLIQUE_VARIABLE = VARIABLE_FACTORY.create("CV2");
    static final Object VALUE_1 = 1;
    static final Object VALUE_2 = 2;
    static final AssignmentVariableConsistencyConstraint CONSTRAINT = AssignmentVariableConsistencyConstraint.builder().left(V1).right(V2).cliqueVariable(CLIQUE_VARIABLE).build();

    static Stream<Arguments> isSatisfiedBy() {
        return Stream.of(
                Arguments.of(Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), true),
                Arguments.of(Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_2)), Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), true),
                Arguments.of(Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_2)), true),
                Arguments.of(Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_2)), false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy(Assignment value1, Assignment value2, boolean satisfied) {
        var assignment = Assignment.of(Map.of(V1, value1, V2, value2));
        assertThat(CONSTRAINT.isSatisfiedBy(assignment)).isEqualTo(satisfied);
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(CONSTRAINT.isSatisfiedBy(Assignment.of(Map.of(V1, Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_2)))))).isTrue();
        assertThat(CONSTRAINT.isSatisfiedBy(Assignment.of(Map.of(V2, Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_2)))))).isTrue();
        assertThat(CONSTRAINT.isSatisfiedBy(Assignment.of(Map.of(V1, Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)), V2, Assignment.of(Map.of(ANOTHER_CLIQUE_VARIABLE, VALUE_2)))))).isTrue();
        assertThat(CONSTRAINT.isSatisfiedBy(Assignment.of(Map.of(V1, Assignment.of(Map.of(ANOTHER_CLIQUE_VARIABLE, VALUE_2)), V2, Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1)))))).isTrue();
    }

    @Test
    void isSatisfiedBy_requiresEverySharedVariableToAgree() {
        val both = AssignmentVariableConsistencyConstraint.builder().left(V1).right(V2)
                .cliqueVariable(CLIQUE_VARIABLE).cliqueVariable(ANOTHER_CLIQUE_VARIABLE).build();
        val left = Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_1));
        val agrees = Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_1));
        val firstDiffers = Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_2, ANOTHER_CLIQUE_VARIABLE, VALUE_1));
        val secondDiffers = Assignment.of(Map.of(CLIQUE_VARIABLE, VALUE_1, ANOTHER_CLIQUE_VARIABLE, VALUE_2));
        assertThat(both.isSatisfiedBy(Assignment.of(Map.of(V1, left, V2, agrees)))).isTrue();
        // Each variable has a matching partner in some value, but no single value matches on both --
        // exactly what a constraint per variable cannot express.
        assertThat(both.isSatisfiedBy(Assignment.of(Map.of(V1, left, V2, firstDiffers)))).isFalse();
        assertThat(both.isSatisfiedBy(Assignment.of(Map.of(V1, left, V2, secondDiffers)))).isFalse();
    }

    @Test
    void isSatisfiedBy_comparesValuesByEqualityNotIdentity() {
        // Integers outside the small-value cache are distinct objects: reference equality would call
        // these different.
        Object thousand1 = Integer.valueOf(1000);
        Object thousand2 = Integer.valueOf(1000);
        assertThat(thousand1).isNotSameAs(thousand2);
        val big1 = Assignment.of(Map.of(CLIQUE_VARIABLE, thousand1));
        val big2 = Assignment.of(Map.of(CLIQUE_VARIABLE, thousand2));
        assertThat(CONSTRAINT.isSatisfiedBy(Assignment.of(Map.of(V1, big1, V2, big2)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(CONSTRAINT.toString()).isEqualTo("<(V1, V2), [CV1] clique consistent>");
    }

    @Test
    void equals() {
        val anotherConstraint = AssignmentVariableConsistencyConstraint.builder().left(V1).right(V2).cliqueVariable(ANOTHER_CLIQUE_VARIABLE).build();
        assertThat(CONSTRAINT).isNotEqualTo(anotherConstraint);
        val sameConstraint = AssignmentVariableConsistencyConstraint.builder().left(V1).right(V2).cliqueVariable(CLIQUE_VARIABLE).build();
        assertThat(CONSTRAINT).isEqualTo(sameConstraint);
    }

    static Stream<Arguments> isEquivalentTo() {
        return Stream.of(
                Arguments.of(CONSTRAINT, true),
                Arguments.of(AllDiffConstraint.builder().variable(V1).variable(V2).build(), false),
                Arguments.of(AssignmentVariableConsistencyConstraint.builder().left(V1).right(V2).cliqueVariable(ANOTHER_CLIQUE_VARIABLE).build(), false),
                Arguments.of(AssignmentVariableConsistencyConstraint.builder().left(V2).right(V1).cliqueVariable(CLIQUE_VARIABLE).build(), true), // reversed
                Arguments.of(AssignmentVariableConsistencyConstraint.builder().left(V2).right(V3).cliqueVariable(CLIQUE_VARIABLE).build(), false)
        );
    }
}
