package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
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
public class AtMostOneConstraintTest {
    @Mock Variable<Boolean> variable1;
    @Mock Variable<Boolean> variable2;
    @Mock Variable<Boolean> variable3;

    AtMostOneConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = AtMostOneConstraint.builder().variables(Set.of(variable1, variable2, variable3)).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingleTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true)))).isTrue();
    }

    @Test
    void isSatisfiedByAllFalse() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, false, variable2, false, variable3, false)))).isTrue();
    }

    @Test
    void isSatisfiedByExactlyOneTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, false, variable3, false)))).isTrue();
    }

    @Test
    void isSatisfiedByTwoTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, true, variable3, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable1, true, variable2, true, variable3, true)))).isFalse();
    }

    @Test
    void getAsBinaryConstraintsProducesPairs() {
        assertThat(constraint.getAsBinaryConstraints()).isPresent();
        assertThat(constraint.getAsBinaryConstraints().get()).hasSize(3);
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(variable1, variable2, variable3), AtMostOne>");
    }
}
