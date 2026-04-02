package org.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a binary constraint with an offset applied to the left operand.
 * This class is an extension of the {@link BinaryConstraint} and allows for a numerical offset
 * adjustment to the left operand before comparison using a specified {@link Operator}.
 * <p>
 * The constraint is considered satisfied if the condition defined by the operator
 * holds true after the offset is applied to the left operand and compared to the right operand.
 * If either operand is null, the constraint is treated as implicitly satisfied.
 * <p>
 * This class supports various numerical types for the offset, including {@code Byte}, {@code Short},
 * {@code Integer}, {@code Long}, {@code Float}, and {@code Double}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryOffsetConstraint  extends BinaryConstraint {
    @NonNull
    Number offset;
    @NonNull
    Operator operator;

    @Override
    public boolean isSatisfiedBy(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return true;
        }
        return operator.compare(offsetValue(left), right);
    }

    @Override
    public String getRelation() {
        return isOffsetNegative()
                ? String.format("%s - %s %s %s", getLeft(), negatedOffset(), operator.symbol, getRight())
                : String.format("%s + %s %s %s", getLeft(), offset, operator.symbol, getRight());
    }

    @Override
    public BinaryConstraint reversed() {
        return BinaryOffsetConstraint.builder()
                .left(getRight())
                .right(getLeft())
                .offset(negatedOffset())
                .operator(operator.reversed())
                .build();
    }

    private Number negatedOffset() {
        return switch (offset) {
            case Byte b -> (byte) -b;
            case Short s -> (short) -s;
            case Integer i -> -i;
            case Long l -> -l;
            case Float f -> -f;
            case Double d -> -d;
            default -> throw new IllegalStateException("Unsupported offset type: " + offset.getClass());
        };
    }

    private boolean isOffsetNegative() {
        return switch (offset) {
            case Byte b -> b < 0;
            case Short s -> s < 0;
            case Integer i -> i < 0;
            case Long l -> l < 0L;
            case Float f -> f < 0.0f;
            case Double d -> d < 0.0;
            default -> throw new IllegalStateException("Unsupported offset type: " + offset.getClass());
        };
    }

    private Number offsetValue(@NonNull Object value) {
        return switch (value) {
            case Byte b -> (byte) (b + offset.byteValue());
            case Short s -> (short) (s + offset.shortValue());
            case Integer i -> i + offset.intValue();
            case Long l -> l + offset.longValue();
            case Float f -> f + offset.floatValue();
            case Double d -> d + offset.doubleValue();
            default -> throw new IllegalStateException("Unsupported value type: " + value.getClass());
        };
    }
}
