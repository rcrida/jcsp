package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.function.Function;

public class BinaryExpressionConstraint implements Constraint {
    private final ExpressionConstraint expressionConstraint;

    public BinaryExpressionConstraint(@NonNull Variable variable1, @NonNull Variable variable2, @NonNull Function<Assignment, Boolean> expression) {
        this.expressionConstraint = new ExpressionConstraint(Set.of(variable1, variable2), expression);
    }

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return expressionConstraint.isSatisfied(assignment);
    }
}
