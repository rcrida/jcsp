package org.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a reversed binary constraint in a constraint satisfaction problem (CSP).
 * <p>
 * This class reverses the logical direction of the relationship defined by an existing
 * {@link BinaryConstraint}. Specifically, the values on the left and right sides of the
 * original constraint are flipped, and the relationship is checked from the reversed perspective.
 * <p>
 * The core functionality includes:
 * - Reversing the satisfaction logic of the wrapped constraint by interchanging
 *   the left and right values.
 * - Adjusting the relation description to indicate that the constraint has been reversed.
 * <p>
 * This is useful for scenarios where the same binary constraint logic needs to be applied
 * from an inverted perspective without duplicating the logic, such as the AC-3 algorithm.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ReversedBinaryConstraint extends BinaryConstraint {
    @NonNull BinaryConstraint constraint;

    @Override
    public boolean isSatisfiedBy(@Nullable Object leftValue, @Nullable Object rightValue) {
        return constraint.isSatisfiedBy(rightValue, leftValue);
    }

    @Override
    public String getRelation() {
        return "reversed " + constraint.getRelation();
    }
}
