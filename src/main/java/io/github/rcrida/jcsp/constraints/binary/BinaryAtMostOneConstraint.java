package io.github.rcrida.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

/**
 * A binary constraint for boolean variables that ensures at most one of the two
 * is {@code true}: equivalent to NAND (¬(left ∧ right)).
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryAtMostOneConstraint extends SymmetricBinaryConstraint<Boolean> {

    @Override
    public boolean isSatisfiedBy(@NonNull Boolean left, @NonNull Boolean right) {
        return !(left && right);
    }

    @Override
    public String getRelation() {
        return "AtMostOne";
    }
}
