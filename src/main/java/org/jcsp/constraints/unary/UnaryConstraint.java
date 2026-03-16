package org.jcsp.constraints.unary;

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
public abstract class UnaryConstraint implements Constraint {
    @NonNull Variable variable;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return isSatisfiedBy(assignment.getValue(variable).orElse(null));
    }

    public abstract boolean isSatisfiedBy(@Nullable Object value);

    @Override
    public String toString() {
        return "<(" + variable + "), " + getRelation() + ">";
    }
}
