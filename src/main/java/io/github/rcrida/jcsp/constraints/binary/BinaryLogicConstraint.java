package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

/**
 * A binary constraint that applies a {@link LogicOperator} to two boolean variables:
 * {@code left <op> right}, e.g. {@code a || b} or {@code a ^ b}.
 * <p>
 * Covers all six symmetric binary boolean connectives: AND, OR, XOR, NAND, NOR, XNOR.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryLogicConstraint extends SymmetricBinaryConstraint<Boolean> {
    @NonNull private final LogicOperator operator;

    public static BinaryLogicConstraint of(@NonNull Variable<Boolean> left,
                                           @NonNull LogicOperator operator,
                                           @NonNull Variable<Boolean> right) {
        return BinaryLogicConstraint.builder().left(left).operator(operator).right(right).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Boolean left, @NonNull Boolean right) {
        return operator.apply(left, right);
    }

    @Override
    public String getRelation() {
        return getLeft() + " " + operator.symbol + " " + getRight();
    }
}
