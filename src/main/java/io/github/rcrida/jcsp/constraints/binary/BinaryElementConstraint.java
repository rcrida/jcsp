package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A binary constraint implementing array element access: {@code result = array[index]}.
 * <p>
 * The left variable holds a 1-based index into a fixed array; the right variable is
 * constrained to equal the value at that position. Out-of-bounds indices are treated
 * as a constraint violation.
 * <p>
 * Equivalent to MiniZinc's {@code element(i, array, result)} constraint.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryElementConstraint<T> extends BinaryConstraint<Integer, T> {
    @NonNull private final List<T> array;

    public static <T> BinaryElementConstraint<T> of(@NonNull Variable<Integer> index,
                                               @NonNull Variable<T> result,
                                               @NonNull List<T> array) {
        return BinaryElementConstraint.<T>builder()
                .left(index)
                .right(result)
                .array(List.copyOf(array))
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Integer index, @NonNull T result) {
        if (index < 1 || index > array.size()) return false;
        return array.get(index - 1).equals(result);
    }

    @Override
    public String getRelation() {
        return getRight() + " = " + array + "[" + getLeft() + "]";
    }
}
