package org.jcsp.constraints.binary;

import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.function.BiFunction;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a pair of variables and evaluates a given expression to determine satisfaction.
 * <p>
 * The constraint is satisfied if the provided assignment meets the boolean condition
 * specified by {@code expression}. This allows for defining complex relationships
 * and dependencies between variables.
 */
@SuperBuilder
public class BinaryExpressionConstraint extends BinaryConstraint {
    @NonNull BiFunction<Object, Object, Boolean> expression;

    @Override
    public boolean isSatisfiedBy(@NonNull Object leftValue, @NonNull Object rightValue) {
        return expression.apply(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return expression.toString();
    }
}
