package io.github.rcrida.jcsp.constraints.binary;

import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryOffsetConstraintTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static final Variable LEFT = VARIABLE_FACTORY.create("left");
    static final Variable RIGHT = VARIABLE_FACTORY.create("right");

    static Stream<Arguments> isSatisfiedBy() {
        return Stream.of(
                Arguments.of(5, Operator.EQ, null, null, true),
                Arguments.of(5, Operator.EQ, null, 19, true),
                Arguments.of(5, Operator.EQ, 0, null, true),
                Arguments.of(5, Operator.EQ, 0, 5, true),
                Arguments.of(5, Operator.EQ, 0, 6, false),
                Arguments.of((byte) 5, Operator.EQ, (byte) 0, (byte) 5, true),
                Arguments.of((short) 5, Operator.EQ, (short) 0, (short) 5, true),
                Arguments.of(5L, Operator.EQ, 0L, 5L, true),
                Arguments.of(5.0f, Operator.EQ, 0.0f, 5.0f, true),
                Arguments.of(5.0, Operator.EQ, 0.0, 5.0, true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void isSatisfiedBy(Number offset, Operator operator, Object left, Object right, boolean expected) {
        val constraint = BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(offset).operator(operator).build();
        val assignmentBuilder = Assignment.builder();
        if (left != null) {
            assignmentBuilder.value(LEFT, left);
        }
        if (right != null) {
            assignmentBuilder.value(RIGHT, right);
        }
        assertThat(constraint.isSatisfiedBy(assignmentBuilder.build())).isEqualTo(expected);
    }

    @Test
    void isSatisfiedBy_unsupportedNumberSubtype() {
        val constraint = BinaryOffsetConstraint.<Number>builder().left(LEFT).right(RIGHT).offset(5).operator(Operator.EQ).build();
        Number unknown = new Number() {
            @Override public int intValue() { return 0; }
            @Override public long longValue() { return 0; }
            @Override public float floatValue() { return 0; }
            @Override public double doubleValue() { return 0; }
        };
        assertThatThrownBy(() -> constraint.isSatisfiedBy(unknown, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported value type: " + unknown.getClass());
    }

    static Stream<Arguments> testToString() {
        return Stream.of(
                Arguments.of((byte) 5, "<(left, right), left + 5 == right>"),
                Arguments.of((byte) -5, "<(left, right), left - 5 == right>"),
                Arguments.of((short) 5, "<(left, right), left + 5 == right>"),
                Arguments.of((short) -5, "<(left, right), left - 5 == right>"),
                Arguments.of(5, "<(left, right), left + 5 == right>"),
                Arguments.of(-5, "<(left, right), left - 5 == right>"),
                Arguments.of(5L, "<(left, right), left + 5 == right>"),
                Arguments.of(-5L, "<(left, right), left - 5 == right>"),
                Arguments.of(5.0f, "<(left, right), left + 5.0 == right>"),
                Arguments.of(-5.0f, "<(left, right), left - 5.0 == right>"),
                Arguments.of(5.0, "<(left, right), left + 5.0 == right>"),
                Arguments.of(-5.0, "<(left, right), left - 5.0 == right>")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testToString(Number offset, String expected) {
        assertThat(BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(offset).operator(Operator.EQ).build()).asString().isEqualTo(expected);
    }

    @Test
    void toString_unsupportedValue() {
        val constraint = BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(new AtomicInteger(0)).operator(Operator.EQ).build();
        assertThatThrownBy(() -> constraint.toString())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported offset type: class java.util.concurrent.atomic.AtomicInteger");
    }

    @Test
    void negatedOffset_unsupportedValue() {
        val constraint = BinaryOffsetConstraint.builder().left(LEFT).right(RIGHT).offset(new AtomicInteger(0)).operator(Operator.EQ).build();
        assertThatThrownBy(() -> constraint.negatedOffset())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported offset type: class java.util.concurrent.atomic.AtomicInteger");
    }
}
