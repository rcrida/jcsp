package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
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
 * provided by the {@link io.github.rcrida.jcsp.assignments.Assignment} and ensures no internal state mutation.
 */
@SuperBuilder
public class AllDiffConstraint<T> extends UniformNaryConstraint<T> {

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        val size = values.size();
        if (size < 2) return true;
        if (size == 2) {
            val iterator = values.iterator();
            return !Objects.equals(iterator.next(), iterator.next());
        }
        val deduped = new HashSet<T>();
        for (T value : values) {
            if (!deduped.add(value)) return false;
        }
        return true;
    }

    @Override
    public String getRelation() {
        return "AllDiff";
    }

    @Override
    public Optional<Set<BinaryConstraint<?, ?>>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
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
