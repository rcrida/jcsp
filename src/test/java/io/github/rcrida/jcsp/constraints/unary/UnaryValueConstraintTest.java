package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryValueConstraintTest {
    static final Object VALUE = 5;
    static final Domain DOMAIN = IntRangeDomain.of(0, 100);
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;

    Variable variable = VARIABLE_FACTORY.create("variable");
    UnaryValueConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = UnaryValueConstraint.builder().variable(variable).value(VALUE).build();
    }

    @Test
    void isSatisfiedBy_true() {
        assertThat(constraint.isSatisfiedBy(VALUE)).isTrue();
    }

    static Stream<Arguments> isSatisfiedBy_false() {
        return DOMAIN.stream()
                .filter(Predicate.not(VALUE::equals))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy_false(Object value) {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(variable, value)))).isFalse();
    }

    @Test
    void isSatisfiedBy_unknown() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(variable), {(5)}>");
    }
}
