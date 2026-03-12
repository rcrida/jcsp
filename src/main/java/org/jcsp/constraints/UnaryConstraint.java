package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.relations.UnaryRelation;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

public record UnaryConstraint(@NonNull Variable variable, @NonNull UnaryRelation relation) implements Constraint {
    public static UnaryConstraint of(@NonNull Variable variable, @NonNull UnaryRelation relation) {
        return new UnaryConstraint(variable, relation);
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return relation.isSatisfied(assignment);
    }

    public boolean isSatisfied(@NonNull Object value) {
        return relation.isSatisfied(value);
    }

    @Override
    public String toString() {
        return "<(" + variable + "), " + relation + ">";
    }
}
