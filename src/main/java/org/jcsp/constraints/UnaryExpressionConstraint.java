package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a unary constraint in a constraint satisfaction problem (CSP) that applies
 * to a single variable and evaluates an expression to determine satisfaction.
 * <p>
 * The constraint is satisfied if the variable is assigned a value that meets the condition
 * defined by the {@code expression}, or unsatisfied if:
 * - The variable is unassigned in the provided assignment.
 * - The value assigned to the variable does not satisfy the {@code expression}.
 *
 * @param variable The single variable to which the constraint applies.
 * @param expression The function that applies a boolean condition to the variable's assigned value.
 */
public record UnaryExpressionConstraint(@NonNull Variable variable, @NonNull Function<@NonNull Object, Boolean> expression) implements Constraint {
    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return Optional.ofNullable(assignment.getValue(variable))
                .map(expression)
                .orElse(false);
    }
}
