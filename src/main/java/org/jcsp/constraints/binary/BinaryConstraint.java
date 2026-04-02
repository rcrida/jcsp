package org.jcsp.constraints.binary;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Represents a binary constraint in a constraint satisfaction problem (CSP).
 * A binary constraint defines a condition or restriction that involves two variables.
 * It specifies the relationship between the values of the left and right variables
 * that must be satisfied in order for the constraint to hold.
 */
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

    @Override
    public Set<Variable> getVariables() {
        return Set.of(left, right);
    }

    public BinaryConstraint reversed() {
        return ReversedBinaryConstraint.builder().left(right).right(left).constraint(this).build();
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + getRelation() + ">";
    }
}
