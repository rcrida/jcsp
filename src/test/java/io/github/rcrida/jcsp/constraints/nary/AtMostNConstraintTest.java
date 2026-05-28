package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AtMostNConstraintTest {
    @Mock Variable<Boolean> v1;
    @Mock Variable<Boolean> v2;
    @Mock Variable<Boolean> v3;
    @Mock Variable<Boolean> v4;

    AtMostNConstraint constraint2;
    AtMostNConstraint constraint3;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        constraint2 = AtMostNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(2).build();
        constraint3 = AtMostNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(3).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByCountWithinBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, false, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAtBound() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountExceedsBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, true)))).isFalse();
    }

    @Test
    void isSatisfiedByAllFalse() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, false, v2, false, v3, false, v4, false)))).isTrue();
    }

    @Test
    void getAsBinaryConstraintsIsEmpty() {
        assertThat(constraint2.getAsBinaryConstraints()).isEmpty();
    }

    @Test
    void getRelation() {
        assertThat(constraint2.getRelation()).isEqualTo("AtMost2");
        assertThat(constraint3.getRelation()).isEqualTo("AtMost3");
    }

    @Test
    void testToString() {
        assertThat(constraint2.toString()).isEqualTo("<(v1, v2, v3, v4), AtMost2>");
    }
}
