package org.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryNotEqualsConstraint extends BinaryConstraint {
    @Override
    public boolean isSatisfiedBy(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return true;
        }
        return !Objects.equals(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " != " + getRight();
    }
}
