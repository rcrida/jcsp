package org.jcsp.constraints;

import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the "all-different" constraint in a constraint satisfaction problem (CSP).
 * This constraint ensures that all variables involved in the assignment have distinct values.
 * <p>
 * The following rules are used for evaluation:
 * - If the assignment contains fewer than two values, the constraint is trivially satisfied.
 * - For two assigned values, the constraint is satisfied if the values are different.
 * - For three or more assigned values, the constraint ensures all values are unique.
 * <p>
 * This implementation is thread-safe as it uses immutable data structures
 * provided by the {@link Assignment} and ensures no internal state mutation.
 */
public record AllDiffConstraint(@NonNull Set<Variable> variables) implements Constraint {

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        final var allValues = assignment.extractPartialAssignment(variables).getValues().values();
        final var allSize = allValues.size();
        if (allSize < 2) {
            return true;
        }
        if (allSize == 2) {
            final var iterator = allValues.iterator();
            final var first = iterator.next();
            final var second = iterator.next();
            return !Objects.equals(first, second);
        }
        final var dedupedValues = new HashSet<>();
        for (Object value : allValues) {
            if (!dedupedValues.add(value))
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "<(" + String.join(", ", variables.stream().map(Object::toString).sorted().toList()) + "), AllDiff>";
    }
}
