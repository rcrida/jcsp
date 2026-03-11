package org.jcsp.relations;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuperBuilder
public class ReversedBinaryRelation extends BinaryRelation {
    @NonNull
    BinaryRelation relation;

    @Override
    public boolean isSatisfied(@Nullable Object left, @Nullable Object right) {
        return relation.isSatisfied(right, left);
    }
}
