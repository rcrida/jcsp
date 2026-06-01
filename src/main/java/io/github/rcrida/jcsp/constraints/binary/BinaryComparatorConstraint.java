package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

/**
 * A binary constraint that compares two variables of the same type using an {@link Operator}:
 * {@code left <op> right}, e.g. {@code v1 <= v2} or {@code v1 != v2}.
 * <p>
 * Works with any {@link Comparable} type — not limited to {@link Number} like
 * {@link BinaryOffsetConstraint}. Useful as the binary decomposition of ordering
 * constraints such as {@link io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryComparatorConstraint<T extends Comparable<T>> extends BinaryConstraint<T, T> {
    @NonNull private final Operator operator;

    public static <T extends Comparable<T>> BinaryComparatorConstraint<T> of(
            @NonNull Variable<T> left, @NonNull Operator operator, @NonNull Variable<T> right) {
        return BinaryComparatorConstraint.<T>builder()
                .left(left).operator(operator).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T leftValue, @NonNull T rightValue) {
        return operator.compare(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return getLeft() + " " + operator.symbol + " " + getRight();
    }
}
