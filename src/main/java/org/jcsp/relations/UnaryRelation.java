package org.jcsp.relations;

import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuperBuilder
public abstract class UnaryRelation implements Relation {
    @NonNull
    Variable variable;

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return isSatisfied(assignment.getValue(variable).orElse(null));
    }

    public abstract boolean isSatisfied(@Nullable Object value);
}
