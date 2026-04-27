package org.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a set of variables and evaluates a given predicate to determine satisfaction.
 * <p>
 * This constraint is defined by:
 * - {@code variables}: The set of variables to which the constraint applies.
 * - {@code predicate}: A predicate that evaluates whether the
 *   provided {@link Assignment} satisfies the constraint.
 * <p>
 * The constraint is satisfied if the provided assignment passes the {@code predicate}.
 * This allows for defining complex relationships and dependencies between multiple variables.
 */
@SuperBuilder
public class PredicateConstraint extends NaryConstraint {
    @NonNull Predicate<Assignment> predicate;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) {
            return true;
        }
        return predicate.test(assignment);
    }

    @Override
    public String getRelation() {
        return predicate.toString();
    }
}
