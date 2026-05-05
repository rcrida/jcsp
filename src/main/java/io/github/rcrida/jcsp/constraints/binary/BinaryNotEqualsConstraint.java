package io.github.rcrida.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryNotEqualsConstraint extends SymmetricBinaryConstraint {
    @Override
    public boolean isSatisfiedBy(@NonNull Object left, @NonNull Object right) {
        return !Objects.equals(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " != " + getRight();
    }
}
