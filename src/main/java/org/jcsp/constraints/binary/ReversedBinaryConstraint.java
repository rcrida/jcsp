package org.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ReversedBinaryConstraint extends BinaryConstraint {
    @NonNull BinaryConstraint constraint;

    @Override
    public boolean isSatisfiedBy(@NonNull Object leftValue, @NonNull Object rightValue) {
        return constraint.isSatisfiedBy(rightValue, leftValue);
    }

    @Override
    public String getRelation() {
        return "reversed " + constraint.getRelation();
    }
}
