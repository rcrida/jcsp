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
public class AtLeastNConstraintTest {
    @Mock Variable<Boolean> v1;
    @Mock Variable<Boolean> v2;
    @Mock Variable<Boolean> v3;
    @Mock Variable<Boolean> v4;

    AtLeastNConstraint constraint2;
    AtLeastNConstraint constraint3;

    @BeforeEach
    void setUp() {
        constraint2 = AtLeastNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(2).build();
        constraint3 = AtLeastNConstraint.builder().variables(Set.of(v1, v2, v3, v4)).n(3).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedByPartialBelowBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAtBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, false, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByCountAboveBound() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, false)))).isTrue();
    }

    @Test
    void isSatisfiedByAllTrue() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, true, v3, true, v4, true)))).isTrue();
    }

    @Test
    void isSatisfiedByCountBelowBoundWhenComplete() {
        assertThat(constraint2.isSatisfiedBy(Assignment.of(Map.of(v1, true, v2, false, v3, false, v4, false)))).isFalse();
    }

    @Test
    void isSatisfiedByAllFalseWhenComplete() {
        assertThat(constraint3.isSatisfiedBy(Assignment.of(Map.of(v1, false, v2, false, v3, false, v4, false)))).isFalse();
    }

    @Test
    void getAsBinaryConstraintsIsEmpty() {
        assertThat(constraint2.getAsBinaryConstraints()).isEmpty();
    }

    @Test
    void getRelation() {
        assertThat(constraint2.getRelation()).isEqualTo("AtLeast2");
        assertThat(constraint3.getRelation()).isEqualTo("AtLeast3");
    }

    @Test
    void testToString() {
        assertThat(constraint2.toString()).isEqualTo("<(v1, v2, v3, v4), AtLeast2>");
    }
}
