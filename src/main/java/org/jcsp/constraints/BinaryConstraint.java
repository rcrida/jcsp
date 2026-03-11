package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.relations.BinaryRelation;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

public record BinaryConstraint(@NonNull Variable left, @NonNull Variable right, @NonNull BinaryRelation relation) implements Constraint {
    public static BinaryConstraint of(@NonNull Variable left, @NonNull Variable right, @NonNull BinaryRelation relation) {
        return new BinaryConstraint(left, right, relation);
    }

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return relation.isSatisfied(assignment);
    }

    @Override
    public boolean isSatisfied(@NonNull Object... values) {
        assert values.length == 2 : "Binary constraint requires exactly two values";
        return relation.isSatisfied(values[0], values[1]);
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + relation + ">";
    }
}
