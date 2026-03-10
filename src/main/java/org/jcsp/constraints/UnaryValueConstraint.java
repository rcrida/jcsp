package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

/**
 * A constraint that restricts a variable to take a specific value in a constraint satisfaction problem (CSP).
 * It enforces that the assigned value of the variable in an assignment must match the specified value.
 * <p>
 * This constraint is unary, meaning it applies to a single variable, and validates the assignment
 * of the variable against an individual, allowed value specified during construction.
 *
 * @param variable The variable to which this constraint applies.
 * @param value The specific value that the variable is constrained to take.
 */
public record UnaryValueConstraint(@NonNull Variable variable, @NonNull Object value) implements Constraint {
    public UnaryValueConstraint {
        assert variable.isAllowedValue(value) : String.format("Invalid constraint value for variable '%s': %s", variable, value);
    }

    @Override
    public boolean isSatisfied(@NonNull Assignment assignment) {
        return value.equals(assignment.getValue(variable));
    }
}
