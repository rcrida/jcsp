package org.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.function.Function;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a set of variables and evaluates a given expression to determine satisfaction.
 * <p>
 * This constraint is defined by:
 * - {@code variables}: The set of variables to which the constraint applies.
 * - {@code expression}: A boolean-valued function that evaluates whether the
 *   provided {@link Assignment} satisfies the constraint.
 * <p>
 * The constraint is satisfied if the provided assignment meets the boolean condition
 * specified by {@code expression}. This allows for defining complex relationships
 * and dependencies between multiple variables.
 */
@SuperBuilder
public class ExpressionConstraint extends NaryConstraint {
    @NonNull Function<Assignment, Boolean> expression;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) {
            return true;
        }
        return expression.apply(assignment);
    }

    @Override
    public String getRelation() {
        return expression.toString();
    }
}
