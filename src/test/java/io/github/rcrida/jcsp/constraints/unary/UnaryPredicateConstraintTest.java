package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryPredicateConstraintTest {
    static final Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static final Variable variable = FACTORY.create("variable");

    UnaryPredicateConstraint<Integer> constraint =
            UnaryPredicateConstraint.<Integer>builder()
                    .variable(variable)
                    .predicate(v -> v > 3)
                    .build();

    @Test
    void satisfiedWhenPredicateHolds() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable, 5)))).isTrue();
    }

    @Test
    void violatedWhenPredicateFails() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable, 2)))).isFalse();
    }

    @Test
    void satisfiedWhenVariableUnassigned() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void satisfiedWhenValueIsNull() {
        assertThat(constraint.isSatisfiedByValue(null)).isTrue();
    }

    @Test
    void getRelationReturnsPrediacteToString() {
        assertThat(constraint.getRelation()).isNotEmpty();
    }
}
