package org.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jspecify.annotations.NonNull;

import java.util.*;

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
@SuperBuilder
public class AllDiffConstraint extends NaryConstraint {

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        final var allValues = assignment.extractPartialAssignment(getVariables()).getValues().values();
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
    public String getRelation() {
        return "AllDiff";
    }

    @Override
    public Optional<Set<BinaryConstraint>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint>();

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryNotEqualsConstraint.builder()
                        .left(variables.get(i))
                        .right(variables.get(j))
                        .build());
            }
        }

        return Optional.of(binaryConstraints);
    }
}
