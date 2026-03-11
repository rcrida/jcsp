package org.jcsp.relations;

import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuperBuilder
public abstract class BinaryRelation implements Relation {
    @NonNull
    Variable left;
    @NonNull
    Variable right;

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return isSatisfied(assignment.getValue(left).orElse(null), assignment.getValue(right).orElse(null));
    }

    public abstract boolean isSatisfied(@Nullable Object left, @Nullable Object right);

    public BinaryRelation reversed() {
        return ReversedBinaryRelation.builder()
                .left(right)
                .right(left)
                .relation(this)
                .build();
    }
}
