package org.jcsp.relations;

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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class UnaryValueRelationTest {
    static final Object VALUE = 5;
    static final Domain DOMAIN = new IntRangeDomain(0, 100);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};

    Variable variable = VARIABLE_FACTORY.create("variable", DOMAIN);
    UnaryValueRelation relation;

    @BeforeEach
    void setUp() {
        relation = UnaryValueRelation.builder()
                .variable(variable)
                .value(VALUE)
                .build();
    }

    @Test
    void isSatisfied_true() {
        assertThat(relation.isSatisfied(VALUE)).isTrue();
    }

    static Stream<Arguments> isSatisfied_false() {
        return DOMAIN.stream()
                .filter(Predicate.not(VALUE::equals))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfied_false(Object value) {
        assertThat(relation.isSatisfied(new Assignment(Map.of(variable, value)))).isFalse();
    }

    @Test
    void isSatisfied_unknown() {
        assertThat(relation.isSatisfied(new Assignment(Map.of()))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(relation.toString()).isEqualTo("{(5)}");
    }
}
