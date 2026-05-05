package io.github.rcrida.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.function.BiPredicate;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a pair of variables and evaluates a given predicate to determine satisfaction.
 * This allows for defining complex relationships and dependencies between variables.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinaryPredicateConstraint extends BinaryConstraint {
    @NonNull BiPredicate<Object, Object> biPredicate;

    @Override
    public boolean isSatisfiedBy(@NonNull Object leftValue, @NonNull Object rightValue) {
        return biPredicate.test(leftValue, rightValue);
    }

    @Override
    public String getRelation() {
        return biPredicate.toString();
    }
}
