package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Set;
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
 *
 * @param variables The constraint operates on a set of variables, making it n-ary in nature.
 * @param expression The logical condition or rule that determines satisfaction.
 */
public record ExpressionConstraint(@NonNull Set<Variable> variables, @NonNull Function<Assignment, Boolean> expression) implements Constraint {
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(variables)) {
            return true;
        }
        return expression.apply(assignment);
    }

    @Override
    public String toString() {
        return "<(" + String.join(", ", variables.stream().map(Object::toString).sorted().toList()) + "), " + expression + ">";
    }
}
