package org.jcsp.constraints.binary;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ReversedBinaryConstraintTest {
    static final List<BinaryTuple> TUPLES = List.of(
            BinaryTuple.of(0, 0),
            BinaryTuple.of(1, 1),
            BinaryTuple.of(2, 4),
            BinaryTuple.of(3, 9)
    );
    static final Domain DOMAIN = new IntRangeDomain(0, 10);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};

    Variable left = VARIABLE_FACTORY.create("left", DOMAIN);
    Variable right = VARIABLE_FACTORY.create("right", DOMAIN);
    ReversedBinaryConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = BinaryTuplesConstraint.builder()
                .left(left)
                .right(right)
                .binaryTuples(TUPLES)
                .build()
                .reversed();
    }

    static Stream<Arguments> isSatisfiedBy_true() {
        return TUPLES.stream().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy_true(BinaryTuple tuple) {
        assertThat(constraint.isSatisfiedBy(new Assignment(Map.of(left, tuple.left(), right, tuple.right())))).isTrue();
    }

    static Stream<Arguments> isSatisfiedBy_false() {
        // all combinations excluding the accepted ones in TUPLES
        return DOMAIN.stream()
                .flatMap(left -> DOMAIN.stream()
                        .map(right -> BinaryTuple.of(left, right))
                )
                .filter(tuple -> !TUPLES.contains(tuple))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy_false(BinaryTuple tuple) {
        assertThat(constraint.isSatisfiedBy(new Assignment(Map.of(left, tuple.left(), right, tuple.right())))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString()).isEqualTo("<(right, left), reversed {(0, 0), (1, 1), (2, 4), (3, 9)}>");
    }
}
