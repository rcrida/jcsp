package org.jcsp.relations;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuperBuilder
public class BinaryNotEqualsRelation extends BinaryRelation {
    @Override
    public boolean isSatisfied(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return true;
        }
        return !Objects.equals(left, right);
    }

    @Override
    public String toString() {
        return left + " != " + right;
    }
}
