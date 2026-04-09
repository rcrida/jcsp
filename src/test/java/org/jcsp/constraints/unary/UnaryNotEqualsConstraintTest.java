package org.jcsp.constraints.unary;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryNotEqualsConstraintTest {
    static final Object VALUE = 5;
    static final Domain DOMAIN = new IntRangeDomain(0, 100);
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable variable = VARIABLE_FACTORY.create("variable");
    UnaryNotEqualsConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = UnaryNotEqualsConstraint.of(variable, VALUE);
    }

    @Test
    void isSatisfiedBy_false() {
        assertThat(constraint.isSatisfiedBy(VALUE)).isFalse();
    }

    static Stream<Arguments> isSatisfiedBy_true() {
        return DOMAIN.stream()
                .filter(Predicate.not(VALUE::equals))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy_true(Object value) {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable, value)))).isTrue();
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(variable), variable != 5>");
    }
}
