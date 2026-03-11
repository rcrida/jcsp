package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.relations.BinaryRelation;
import org.jcsp.relations.Relation;
import org.jcsp.relations.UnaryRelation;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public record UnaryConstraint(@NonNull Variable variable, @NonNull UnaryRelation relation) implements Constraint {
    public static UnaryConstraint of(@NonNull Variable variable, @NonNull UnaryRelation relation) {
        return new UnaryConstraint(variable, relation);
    }

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return relation.isSatisfied(assignment);
    }

    @Override
    public boolean isSatisfied(@NonNull Object... values) {
        assert values.length == 1 : "Unary constraint requires exactly one value";
        return relation.isSatisfied(values[0]);
    }

    @Override
    public String toString() {
        return "<(" + variable + "), " + relation + ">";
    }
}
