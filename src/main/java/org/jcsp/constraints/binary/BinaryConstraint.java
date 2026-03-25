package org.jcsp.constraints.binary;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@NonFinal
@SuperBuilder
public abstract class BinaryConstraint implements Constraint {
    @NonNull Variable left;
    @NonNull Variable right;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return isSatisfiedBy(assignment.getValue(left).orElse(null), assignment.getValue(right).orElse(null));
    }

    public abstract boolean isSatisfiedBy(@Nullable Object leftValue, @Nullable Object rightValue);

    public BinaryConstraint reversed() {
        return ReversedBinaryConstraint.builder().left(right).right(left).constraint(this).build();
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + getRelation() + ">";
    }
}
