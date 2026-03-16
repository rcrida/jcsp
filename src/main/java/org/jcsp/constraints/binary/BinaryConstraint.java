package org.jcsp.constraints.binary;

import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.relations.BinaryRelation;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

public record BinaryConstraint(@NonNull Variable left, @NonNull Variable right, @NonNull BinaryRelation relation) implements Constraint {
    public static BinaryConstraint of(@NonNull Variable left, @NonNull Variable right, @NonNull BinaryRelation relation) {
        return new BinaryConstraint(left, right, relation);
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return relation.isSatisfied(assignment);
    }

    public boolean isSatisfied(@NonNull Object leftValue, @NonNull Object rightValue) {
        return relation.isSatisfied(leftValue, rightValue);
    }

    public BinaryConstraint reversed() {
        return new BinaryConstraint(right, left, relation.reversed());
    }

    @Override
    public String getRelation() {
        return "";
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + relation + ">";
    }
}
