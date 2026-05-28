package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryEqualsConstraint<T> extends SymmetricBinaryConstraint<T> {

    public static <T> BinaryEqualsConstraint<T> of(@NonNull Variable<T> left, @NonNull Variable<T> right) {
        return BinaryEqualsConstraint.<T>builder().left(left).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull T left, @NonNull T right) {
        return Objects.equals(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " == " + getRight();
    }
}
